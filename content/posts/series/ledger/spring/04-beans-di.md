---
title: "빈과 의존성 주입"
slug: "ledger-spring-beans-di"
category: "development"
publishedAt: "2026-10-08"
summary: "Spring이 객체를 대신 만들고 연결해 주는 빈과 의존성 주입(DI)을, Ledger의 @Controller, @Service, @Repository, @Autowired, @Resource, XML 빈 정의로 설명합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 4
---
## 이 글에서 할 일

Ledger 코드에는 `new SettingsServiceImpl()`이나 `new SettingsDAO()` 같은 코드가 없습니다. 그런데도 Controller에서 `settingsService`를 바로 씁니다. Spring이 객체를 대신 만들고 넣어 주기 때문입니다. 이 원리를 이해합니다.

## 빈: Spring이 관리하는 객체

**빈**(Bean)은 Spring이 만들어서 [컨텍스트](/posts/ledger-spring-two-contexts)에 담아 두는 객체입니다. 앱이 시작될 때 한 번 만들어지고, 같은 빈을 여러 곳에서 함께 씁니다(기본이 싱글톤).

빈을 만드는 방법은 두 가지입니다.

### 방법 1. 어노테이션 + 컴포넌트 스캔

```xml
<context:component-scan base-package="ledger"/>
```

`ledger` 패키지 아래에서 다음 어노테이션이 붙은 클래스를 찾아 빈으로 만듭니다.

| 어노테이션 | 붙이는 곳 | Ledger 예 |
|---|---|---|
| `@Controller` | 요청을 받는 클래스 | `SettingsApiController` |
| `@Service` | 서비스 구현 | `@Service("settingsService") SettingsServiceImpl` |
| `@Repository` | DB 접근 | `@Repository("settingsDAO") SettingsDAO` |
| `@Component` | 그 밖의 빈 | `RecurringScheduler`, `LoginAttemptLimiter` |

네 개 모두 "빈으로 만들어라"는 뜻은 같고, 역할을 표시하는 이름입니다. `@Repository`는 DB 예외를 Spring의 공통 예외로 바꿔 주는 처리도 받습니다. 괄호 안의 문자열(`"settingsService"`)은 빈의 이름입니다.

### 방법 2. XML에 직접 정의

남이 만든 라이브러리 클래스에는 어노테이션을 붙일 수 없습니다. 이럴 때는 XML에 적습니다.

`context-datasource.xml`

```xml
<bean id="passwordEncoder" class="org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder"/>

<bean id="dataSource-postgresql" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">
    <property name="jdbcUrl" value="${Globals.Ledger.Postgre.Url}"/>
    ...
</bean>
```

| 부분 | 뜻 |
|---|---|
| `id` | 빈 이름 |
| `class` | 만들 클래스 |
| `property` | setter로 값을 넣음 (`setJdbcUrl(...)`) |
| `ref` | 다른 빈을 넣음 |
| `destroy-method` | 앱이 끝날 때 호출할 메서드. 커넥션 풀을 닫음 |

## 의존성 주입 (DI)

**의존성**은 "이 클래스가 일하려면 필요한 다른 객체"입니다. Controller는 Service가, Service는 DAO가 필요합니다. **주입**은 그 객체를 내가 만들지 않고 밖(Spring)에서 넣어 받는 것입니다.

```text
직접 만들기:  SettingsApiController 안에서  new SettingsServiceImpl()
주입 받기:    Spring이 SettingsServiceImpl 빈을 만들어 Controller의 필드에 넣어 줌
```

### @Autowired: 타입으로 찾기

```java
@Autowired
private SettingsService settingsService;
```

Spring이 `SettingsService` **타입**의 빈을 찾아 넣습니다. `SettingsServiceImpl`이 그 인터페이스를 구현하므로 그것이 들어갑니다. 같은 타입의 빈이 둘이면 어느 것인지 몰라 시작할 때 오류가 납니다.

### @Resource: 이름으로 찾기

`SettingsDAO.java`

```java
@Resource(name = "sqlSession-ledger-postgre")
private SqlSessionTemplate sqlSession;
```

`@Resource(name = ...)`는 **이름**으로 빈을 찾습니다. XML에서 `id="sqlSession-ledger-postgre"`로 만든 빈입니다. DB가 여러 개인 프로젝트에서 어느 연결을 쓸지 이름으로 분명하게 고르는 방식으로, 전자정부 프레임워크 프로젝트에서 많이 보입니다. `@Resource`는 Spring이 아니라 Java 표준(`jakarta.annotation`) 어노테이션이라 `jakarta.annotation-api` 의존성이 필요합니다.

### @Value: 설정값 넣기

```java
@Value("${Globals.Ledger.SignupCode}")
private String signupCode;
```

빈이 아니라 설정 파일의 값을 넣습니다([설정값과 환경 변수](/posts/ledger-spring-properties)).

### @PostConstruct: 주입이 끝난 뒤 한 번

`AuthApiController.java`

```java
// 없는 아이디도 BCrypt 비교를 한 번 해서 응답 시간으로 아이디 존재 여부가 드러나지 않게 한다
private String dummyHash;

@PostConstruct
public void init() {
    dummyHash = passwordEncoder.encode("ledger-dummy-password");
}
```

생성자가 실행될 때는 아직 `passwordEncoder`가 주입되지 않았습니다. `@PostConstruct` 메서드는 **주입이 모두 끝난 뒤** 한 번 호출되므로, 주입받은 빈을 써서 준비 작업을 할 수 있습니다. 이 더미 해시가 왜 필요한지는 [로그인 보안 글](/posts/ledger-auth-signup-login)에서 다룹니다.

## 빈 연결 지도

```text
SettingsApiController
  ├─ @Autowired SettingsService ──▶ SettingsServiceImpl
  │                                    └─ @Autowired SettingsDAO
  │                                          └─ @Resource sqlSession-ledger-postgre (XML)
  │                                                └─ sqlSessionFactory → dataSource-postgresql (XML)
  ├─ @Autowired EntryService ──▶ EntryServiceImpl ...
  └─ @Autowired AccountService ──▶ AccountServiceImpl ...
```

## 필드 주입과 생성자 주입

Ledger의 Controller, Service, DAO는 필드에 `@Autowired`를 붙이는 **필드 주입**을 씁니다. 전자정부 프레임워크 프로젝트와 같은 모양입니다.

생성자에서 바로 다른 객체를 만들어야 하는 클래스는 **생성자 주입**을 씁니다. 고정 항목 자동 기록기는 트랜잭션 매니저를 받아 그 자리에서 `TransactionTemplate`을 만듭니다.

`ledger/recurring/RecurringGenerator.java`

```java
private final TransactionTemplate tx;

@Autowired
public RecurringGenerator(PlatformTransactionManager transactionManager) {
    this.tx = new TransactionTemplate(transactionManager);
}
```

필드 주입은 객체가 만들어진 **뒤에** 값이 들어오므로, 생성자 안에서는 아직 쓸 수 없습니다. 생성자 주입은 생성자의 파라미터로 받으므로 생성자 안에서 바로 쓸 수 있고, 필드를 `final`로 둘 수 있습니다. 정기 이체 기록기(`TransferGenerator`)도 같은 방식입니다.

## 정리

- 빈은 Spring이 만들고 관리하는 객체입니다. 어노테이션 + 컴포넌트 스캔, 또는 XML로 만듭니다.
- `@Autowired`는 타입으로, `@Resource(name)`은 이름으로 빈을 주입합니다.
- `@PostConstruct`는 주입이 끝난 뒤 준비 작업을 합니다.
- 대부분은 필드 주입을, 생성자에서 의존성이 필요한 자동 기록기는 생성자 주입을 씁니다.
