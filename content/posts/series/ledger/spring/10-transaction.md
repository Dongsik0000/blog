---
title: "트랜잭션으로 여러 작업을 한 묶음으로"
slug: "ledger-spring-transaction"
category: "development"
publishedAt: "2026-10-08"
summary: "트랜잭션이 필요한 이유와 @Transactional의 동작(롤백 조건, 프록시), Ledger가 Controller, Service, TransactionTemplate 세 곳에서 트랜잭션을 거는 방법을 실제 코드로 설명합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 10
---
## 이 글에서 할 일

가입하면 사용자, 기본 카테고리, 기본 결제수단 세 가지를 DB에 넣습니다. 그런데 두 번째에서 오류가 나면 어떻게 될까요? 사용자만 있고 카테고리가 없는 반쪽짜리 계정이 남습니다. **트랜잭션**은 여러 DB 작업을 "모두 성공하거나, 모두 없던 일로" 만드는 장치입니다.

## 기본 개념

| 용어 | 뜻 |
|---|---|
| 트랜잭션 | 하나로 묶인 DB 작업들 |
| 커밋(commit) | 묶음 전체를 확정 |
| 롤백(rollback) | 묶음 전체를 취소 |

## 설정

`context-datasource.xml` (루트 컨텍스트)

```xml
<bean id="transactionManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
    <property name="dataSource" ref="dataSource-postgresql"/>
</bean>
```

`dispatcher-servlet.xml` (서블릿 컨텍스트)

```xml
<tx:annotation-driven transaction-manager="transactionManager"/>
```

트랜잭션 매니저는 DB 연결을 열고 커밋, 롤백을 실행하는 객체입니다. `tx:annotation-driven`이 `@Transactional`을 읽게 합니다. 이 설정이 서블릿 컨텍스트에도 있어야 하는 이유는 [두 컨텍스트 글](/posts/ledger-spring-two-contexts)에서 설명했습니다.

## @Transactional은 어떻게 동작하나

```java
@Transactional
@ResponseBody
@PostMapping("/auth/signup")
public Response signup(...) {
    authService.insertUser(user);
    authService.insertDefaultCategories(user);
    authService.insertDefaultPaymentMethods(user);
    return Response.of(Constants.SUCCESS);
}
```

Spring은 `@Transactional`이 붙은 빈을 **프록시**(대리 객체)로 감쌉니다. 바깥에서 메서드를 부르면 프록시가 먼저 받아 다음 순서로 실행합니다.

```text
프록시: 트랜잭션 시작
  └─ 원래 메서드 실행
        ├─ 정상 종료 → 커밋
        └─ RuntimeException이 밖으로 나옴 → 롤백, 예외는 그대로 다시 던짐
```

알아 둘 점이 세 가지 있습니다.

1. **롤백은 `RuntimeException`(과 `Error`)에서만** 일어납니다. 기본 설정에서 체크 예외(`IOException` 등)는 커밋됩니다.
2. **예외를 메서드 안에서 잡으면 롤백되지 않습니다.** 그래서 Ledger의 Controller는 try/catch를 쓰지 않습니다([공통 예외 처리](/posts/ledger-spring-exception-handling)).
3. **같은 클래스 안에서 자기 메서드를 부르면** 프록시를 거치지 않아 `@Transactional`이 적용되지 않습니다.

### 응답 코드로 끝나는 경우는 롤백이 아님

```java
if (authService.selectUserByUsername(username) != null) {
    return Response.of(Constants.SIGNUP_FAIL_EXISTS);
}
```

`return`으로 실패 응답을 보내면 예외가 아니므로 **커밋**됩니다. 그래서 검사는 DB에 쓰기 **전에** 모두 끝내고, 쓰기를 시작한 뒤의 실패는 예외로 처리합니다.

## Ledger의 세 가지 트랜잭션

### 1. Controller 메서드

요청 하나 = 트랜잭션 하나. 가입, 카테고리 삭제와 순서 변경, 통장 이체처럼 여러 테이블을 함께 바꾸는 요청에 붙입니다.

### 2. Service 메서드

`ledger/entry/service/impl/EntryServiceImpl.java`

```java
@Override
@Transactional
public int insertEntry(Map<String, Object> param) {
    int inserted = entryDAO.insertEntry(param);
    adjust(param, null, 0);   // 연결된 적금 자산 잔액 조정. 실패하면 IllegalStateException
    return inserted;
}
```

거래를 넣을 때 적금 자산 잔액도 함께 바꿉니다. 이 메서드는 거래 화면과 고정 항목 자동 기록 **둘 다** 부르므로, 어느 쪽에서 불러도 묶음이 되도록 Service에 걸었습니다. Controller의 트랜잭션 안에서 부르면 기존 트랜잭션에 **합류**하고(기본 전파 방식 `REQUIRED`), 트랜잭션 밖에서 부르면 새로 시작합니다.

```java
if (assetDAO.adjustCashAsset(...) != 1) {
    throw new IllegalStateException("연결된 자산을 찾을 수 없거나 잔액이 허용 범위를 벗어납니다");
}
```

자산 조정이 한 줄도 바뀌지 않으면 `RuntimeException`을 던져, 방금 넣은 거래까지 롤백합니다.

### 3. TransactionTemplate: 반복 안에서 하나씩

고정 항목 자동 기록은 스케줄러가 모든 사용자의 항목을 돌며 실행합니다. 메서드 전체를 하나의 트랜잭션으로 묶으면, 항목 하나만 실패해도 **모든 사용자의 기록이 취소**됩니다. 그래서 항목의 결제 한 건마다 따로 묶습니다.

`ledger/recurring/RecurringGenerator.java` (일부)

```java
private final TransactionTemplate tx;

@Autowired
public RecurringGenerator(PlatformTransactionManager transactionManager) {
    this.tx = new TransactionTemplate(transactionManager);
}

// ...반복문 안에서
Boolean done = tx.execute(status -> generateOne(item, due, recordedAmount, usedFx));
```

```java
// 한 트랜잭션: 처리 기록 → 거래 → 기록에 거래 연결. 기록이 이미 있으면(처리됨·건너뜀) 아무것도 하지 않는다
private boolean generateOne(...) { ... }
```

`TransactionTemplate.execute`는 람다 안의 코드를 트랜잭션으로 실행합니다. `@Transactional`은 메서드 단위라 이렇게 반복문 안의 일부만 묶기 어렵고, 위 3번 주의점(같은 클래스 안의 호출)에도 걸립니다. 코드로 범위를 정하는 `TransactionTemplate`이 맞는 경우입니다.

그리고 바깥 반복은 항목별로 예외를 잡아 다음 항목을 계속합니다.

```java
// 스케줄러: 모든 사용자의 활성 항목. 한 항목이 실패해도 로그만 남기고 다음 항목을 계속한다
for (Map<String, Object> item : recurringService.selectActiveItems()) {
    try {
        created += generate(item, today, holidays);
    } catch (RuntimeException e) {
        logger.error("고정 항목 자동 기록 실패: id={}", item.get("id"), e);
    }
}
```

여기서 try/catch를 쓰는 것은 괜찮습니다. 트랜잭션은 이미 안쪽 `tx.execute`에서 끝났고(롤백 완료), 바깥은 "다음 항목으로 넘어가기"만 하기 때문입니다.

## 정리

- 트랜잭션은 여러 DB 작업을 모두 커밋하거나 모두 롤백합니다.
- `@Transactional`은 프록시로 동작하며, `RuntimeException`이 메서드 밖으로 나올 때 롤백합니다.
- Ledger는 요청 단위는 Controller에, 여러 곳이 함께 쓰는 작업은 Service에, 반복 안의 한 건 단위는 `TransactionTemplate`으로 겁니다.
- 검사는 DB에 쓰기 전에 끝내고, 쓰기 시작한 뒤의 실패는 예외로 알립니다.
