---
title: "스케줄러와 꺼져 있던 동안 따라잡기"
slug: "ledger-domain-scheduler"
category: "development"
publishedAt: "2026-10-08"
summary: "매일 00:05에 고정 항목과 정기 이체를 기록하는 @Scheduled 설정, 한국 시간 기준으로 돌게 하는 방법, 앱이 시작될 때 한 번 더 실행해 서버가 꺼져 있던 동안의 결제일을 채우는 방법을 설명합니다."
series: "ledger"
chapter: "domain"
seriesOrder: 5
---
## 이 글에서 할 일

고정 항목과 정기 이체는 사람이 아무것도 하지 않아도 결제일에 기록되어야 합니다. 이 일은 **스케줄러**가 맡습니다. 그런데 서버가 배포나 장애로 꺼져 있던 동안 결제일이 지나가면 어떻게 될까요? 이 글에서는 그 두 가지를 봅니다.

## 스케줄러 켜기

`dispatcher-servlet.xml`

```xml
<!-- @Scheduled 활성화: 고정 항목 자동 생성(매일 00:05). 스케줄 메서드를 가진 빈은 이 컨텍스트(ledger 패키지)에 둔다 -->
<task:annotation-driven scheduler="scheduler"/>
<task:scheduler id="scheduler" pool-size="1"/>
```

| 설정 | 뜻 |
|---|---|
| `task:annotation-driven` | `@Scheduled`를 읽어 실행 |
| `pool-size="1"` | 예약 작업을 실행할 스레드 1개. 작업이 하나라 충분하고, 같은 작업이 겹쳐 돌지 않음 |

[트랜잭션 설정](/posts/ledger-spring-two-contexts)처럼 스캔된 빈이 있는 서블릿 컨텍스트에 둡니다.

## RecurringScheduler

`ledger/recurring/RecurringScheduler.java`

```java
// 고정 항목·정기 이체 자동 기록: 매일 00:05(한국 시간) + 앱 기동 직후(꺼져 있던 동안의 결제일 따라잡기)
@Component
public class RecurringScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void daily() {
        run("매일");
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onStartup() {
        run("기동");
    }

    // 기동 중 DB 오류 등으로 실패해도 앱 기동은 막지 않는다(다음 00:05 에 다시 시도)
    private void run(String trigger) {
        try {
            int created = recurringGenerator.generateAll(LocalDate.now(SEOUL));
            logger.info("고정 항목 자동 기록({}): {}건", trigger, created);
        } catch (RuntimeException e) {
            logger.error("고정 항목 자동 기록({}) 실패", trigger, e);
        }
        try {
            int created = transferGenerator.generateAll(LocalDate.now(SEOUL));
            logger.info("정기 이체 자동 기록({}): {}건", trigger, created);
        } catch (RuntimeException e) {
            logger.error("정기 이체 자동 기록({}) 실패", trigger, e);
        }
    }
}
```

### cron 표현식

```text
"0 5 0 * * *"
 │ │ │ │ │ └ 요일: 매일
 │ │ │ │ └── 월: 매월
 │ │ │ └──── 일: 매일
 │ │ └────── 시: 0시
 │ └──────── 분: 5분
 └────────── 초: 0초
```

Spring의 cron은 **초부터** 6자리입니다(리눅스 crontab은 분부터 5자리).

### 시간대를 꼭 적는 이유

```java
@Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
...
LocalDate.now(SEOUL)
```

서버나 컨테이너의 기본 시간대가 UTC라면 "0시 5분"은 한국 시간 오전 9시 5분이 됩니다. 그러면 결제일 거래가 아침에야 생기고, 날짜 계산(`LocalDate.now()`)도 한국 날짜와 하루 어긋날 수 있습니다. 실행 시각(`zone`)과 날짜 계산(`LocalDate.now(SEOUL)`) 모두 한국 시간을 명시했고, Docker 이미지에도 시간대를 고정해 이중으로 맞췄습니다.

```dockerfile
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Seoul ..."
```

## 꺼져 있던 동안 따라잡기

배포는 컨테이너를 새로 만드는 방식이라, 마침 00:05에 배포 중이면 그날 실행을 놓칩니다. 서버가 하루 꺼져 있으면 그날 결제일도 놓칩니다.

```java
@EventListener(ContextRefreshedEvent.class)
public void onStartup() {
    run("기동");
}
```

`ContextRefreshedEvent`는 Spring 컨텍스트가 준비를 마쳤을 때 발생하는 이벤트입니다. **앱이 시작될 때마다 한 번** 같은 작업을 실행합니다.

따라잡기가 동작하는 이유는 [자동 기록](/posts/ledger-domain-recurring)이 "오늘 결제일만"이 아니라 "**지난달부터 오늘까지 결제일이 왔는데 처리 기록이 없는 것**"을 찾기 때문입니다. 처리 기록이 있는 달은 건너뛰므로, 여러 번 실행해도 결과는 같습니다(멱등).

```text
10/5 결제일, 서버가 10/4 ~ 10/6 꺼져 있었음
10/6 앱 시작 → onStartup → "10월분 결제일(10/5)이 지났고 기록 없음" → 거래 날짜 10/5로 기록
10/7 00:05  → daily     → "10월분 기록 있음" → 건너뜀
```

거래 날짜는 실행한 날이 아니라 **결제일**(10/5)로 들어갑니다.

### 시작 실패가 앱을 막지 않게

```java
// 기동 중 DB 오류 등으로 실패해도 앱 기동은 막지 않는다(다음 00:05 에 다시 시도)
```

`onStartup`에서 예외가 밖으로 나가면 앱 시작 자체가 실패할 수 있습니다. 자동 기록은 부가 기능이라, 실패해도 로그만 남기고 앱은 뜨게 합니다. 다음 00:05나 다음 시작 때 다시 시도합니다.

고정 항목과 정기 이체도 각각 try/catch로 감싸, 한쪽이 실패해도 다른 쪽은 실행됩니다.

## 로그로 확인하기

```text
... INFO  ledger.recurring.RecurringScheduler - 고정 항목 자동 기록(기동): 0건
... INFO  ledger.recurring.RecurringScheduler - 정기 이체 자동 기록(기동): 0건
```

배포한 뒤 컨테이너 로그에서 이 두 줄을 보면 스케줄러가 동작한 것입니다. 로그 보는 법은 [Oracle Cloud 시리즈의 로그 글](/posts/oracle-cloud-ops-logs)에 있습니다.

## 정리

- `@Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")`로 매일 한국 시간 00:05에 실행합니다.
- 실행 시각과 날짜 계산 모두 한국 시간을 명시하고, Docker 이미지 시간대도 고정했습니다.
- `ContextRefreshedEvent`로 앱이 시작될 때 한 번 더 실행해, 꺼져 있던 동안의 결제일을 채웁니다.
- 자동 기록은 처리 기록 기준이라 여러 번 실행해도 결과가 같고, 실패해도 앱 시작을 막지 않습니다.
