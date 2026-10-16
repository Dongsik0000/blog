---
title: "설정값과 환경 변수"
slug: "ledger-spring-properties"
category: "development"
publishedAt: "2026-10-08"
summary: "globals.properties의 ${환경변수:기본값} 형식으로 로컬과 서버의 설정을 한 파일에서 다루는 방법, XML의 ${...}와 @Value로 값을 꺼내는 방법, 비밀값을 저장소에 두지 않는 원칙을 설명합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 11
---
## 이 글에서 할 일

DB 비밀번호나 API 키는 내 PC와 서버에서 값이 다르고, 저장소에 올리면 안 됩니다. Ledger는 설정 파일 하나로 두 환경을 모두 다루면서 비밀값은 저장소 밖에 둡니다. 그 방법을 봅니다.

## 설정 파일

`src/main/resources/egovProps/globals.properties`

```properties
# 값은 환경변수를 우선하고, 없으면 콜론 뒤 기본값(로컬 개발용)을 쓴다.
# 서버에서는 /etc/ledger/app.env 로 주입한다. 비밀값을 이 파일에 직접 쓰지 않는다.

Globals.Ledger.Postgre.Url=${LEDGER_DB_URL:jdbc:postgresql://localhost:5432/ledger}
Globals.Ledger.Postgre.UserName=${LEDGER_DB_USER:ledger_app}
Globals.Ledger.Postgre.Password=${LEDGER_DB_PASSWORD:ledger_app}

# 회원가입 시 입력해야 하는 코드
Globals.Ledger.SignupCode=${LEDGER_SIGNUP_CODE:dev-signup}

# 공공데이터포털 특일정보 API 키 (없으면 공휴일 가져오기 버튼 비활성)
Globals.Ledger.HolidayApiKey=${LEDGER_HOLIDAY_API_KEY:}

# Alpha Vantage 환율/주식 시세 API 키 (비어 있으면 외화·주식 자동 계산 불가)
Globals.Ledger.MarketApiKey=${LEDGER_MARKET_API_KEY:}
Globals.Ledger.MarketRealtime=${LEDGER_MARKET_REALTIME:false}
```

### `${이름:기본값}` 읽는 법

```text
Globals.Ledger.Postgre.Password=${LEDGER_DB_PASSWORD:ledger_app}
                                  └──── 환경 변수 ────┘ └ 없으면 ┘
```

Spring이 값을 채울 때 `LEDGER_DB_PASSWORD`라는 **환경 변수**(또는 Java 시스템 속성)를 찾고, 없으면 콜론 뒤의 기본값을 씁니다.

| 환경 | `LEDGER_DB_PASSWORD` 환경 변수 | 결과 |
|---|---|---|
| 내 PC | 없음 | `ledger_app` (로컬 기본값) |
| 서버 | 있음 (`app.env`에서) | 서버의 실제 비밀번호 |

기본값은 **로컬 개발용**이라 저장소에 있어도 괜찮은 값만 둡니다. API 키처럼 기본값을 둘 수 없는 것은 `${LEDGER_HOLIDAY_API_KEY:}`처럼 비워 두고, 앱은 값이 비었으면 그 기능을 끕니다.

## 값을 꺼내는 두 가지 방법

### XML에서 `${...}`

`context-datasource.xml`

```xml
<context:property-placeholder location="classpath:/egovProps/globals.properties"/>

<bean id="dataSource-postgresql" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">
    <property name="jdbcUrl" value="${Globals.Ledger.Postgre.Url}"/>
    <property name="username" value="${Globals.Ledger.Postgre.UserName}"/>
    <property name="password" value="${Globals.Ledger.Postgre.Password}"/>
</bean>
```

`property-placeholder`가 파일을 읽고, XML의 `${Globals...}`를 값으로 바꿉니다. 그 값이 다시 `${LEDGER_DB_URL:...}`이므로 한 번 더 환경 변수로 풀립니다.

### Java에서 `@Value`

```java
@Value("${Globals.Ledger.SignupCode}")
private String signupCode;
```

필드에 값이 주입됩니다. 이 필드가 있는 빈은 서블릿 컨텍스트에 있으므로, 서블릿 컨텍스트에도 `property-placeholder`가 있어야 합니다([두 컨텍스트](/posts/ledger-spring-two-contexts) 참고).

## 서버에서 환경 변수 넣기

서버는 Docker 컨테이너로 실행하고, compose가 서버의 파일에서 환경 변수를 읽어 넣습니다.

`docker-compose.yml` (일부)

```yaml
    env_file:
      - /etc/ledger/app.env
```

저장소에는 값이 빈 예시만 있습니다.

`deploy/app.env.example`

```text
LEDGER_DB_URL=jdbc:postgresql://127.0.0.1:5432/ledger
LEDGER_DB_USER=ledger_app
LEDGER_DB_PASSWORD=
LEDGER_SIGNUP_CODE=
LEDGER_HOLIDAY_API_KEY=
LEDGER_MARKET_API_KEY=
LEDGER_MARKET_REALTIME=false
```

**서버에서는 `/etc/ledger/app.env`를 직접 만들어야 합니다.** 예시 파일은 값의 이름을 제공할 뿐이며, Git에서 내려받거나 이미지를 빌드해도 운영 환경 파일이 만들어지지는 않습니다.

처음 설치하는 서버의 Ledger 저장소 루트에서 실행합니다.

```bash
sudo install -d -m 700 -o root -g root /etc/ledger
sudo install -m 600 -o root -g root deploy/app.env.example /etc/ledger/app.env
sudoedit /etc/ledger/app.env
```

두 번째 명령은 파일을 덮어쓰므로, 기존 운영 파일이 있는 서버에서는 복사하지 않고 `sudoedit`로 필요한 값만 수정합니다. DB 비밀번호를 DB 계정의 실제 값과 맞추고, 가입을 허용하려면 초대 코드를 넣습니다. API 키는 사용할 기능에 맞춰 입력합니다.

저장소의 `.gitignore`에도 `app.env`가 등록되어 있습니다. 운영 파일은 저장소 밖에 두고, 비밀값을 Git에 추가하지 않습니다. 각 변수와 컨테이너 실행·확인 절차는 [Compose와 app.env 글](/posts/ledger-deploy-compose-env)에서 이어집니다.

이 파일은 **Compose가 읽어 컨테이너 환경 변수로 주입**합니다. Spring이 `app.env`를 직접 읽는 구조는 아닙니다. `env_file`의 역할은 [Docker 공식 문서](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/)에서 확인했습니다(2026-10-07).

## 운영에서 안전한 기본값

설정이 빠졌을 때 앱이 **안전한 쪽으로** 동작하게 만들었습니다.

```java
// 환경변수 LEDGER_SIGNUP_CODE 가 비어 있으면 가입 불가. 비교는 상수 시간
private boolean isSignupCodeValid(String code) {
    if (signupCode == null || signupCode.isBlank()) {
        return false;
    }
    return MessageDigest.isEqual(signupCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
}
```

로컬 기본값 `dev-signup`은 환경 변수가 **아예 없을 때**만 쓰입니다. 환경 변수가 있지만 비어 있으면 기본값 대신 빈 값이 들어가고 **가입이 막힙니다.**

운영 이미지는 이 성질을 이용해 `Dockerfile`에서 기본값을 정해 둡니다.

```dockerfile
ENV LEDGER_LOG_LEVEL=INFO \
    LEDGER_SIGNUP_CODE=""
```

- `LEDGER_SIGNUP_CODE=""`: `app.env`에 가입 코드를 넣지 않고 서버를 띄워도 아무나 가입할 수 없습니다. 가입이 필요할 때만 `app.env`에 코드를 넣습니다.
- `LEDGER_LOG_LEVEL=INFO`: 로컬에서는 `DEBUG`로 MyBatis가 실행한 SQL과 파라미터까지 보이지만, 운영에서는 금액이나 메모 같은 개인 기록이 로그에 남지 않게 `INFO`로 낮춥니다(`logback.xml`의 `${LEDGER_LOG_LEVEL:-DEBUG}`).

## 정리

- `globals.properties`는 `${환경변수:로컬 기본값}` 형식으로 로컬과 서버를 한 파일에서 다룹니다.
- XML은 `property-placeholder` + `${...}`, Java는 `@Value`로 값을 꺼냅니다.
- 서버는 compose의 `env_file`로 서버의 `app.env`를 읽고, 저장소에는 값이 빈 예시만 둡니다.
- 설정이 비면 기능을 끄는 쪽(가입 불가, API 기능 비활성)으로 안전하게 동작합니다.
