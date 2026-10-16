---
title: "JDBC에서 MyBatis로 (설정 읽기)"
slug: "ledger-mybatis-mybatis-setup"
category: "development"
publishedAt: "2026-10-08"
summary: "Java가 DB와 이야기하는 JDBC부터 커넥션 풀 HikariCP, MyBatis의 SqlSessionFactory와 SqlSessionTemplate까지 context-datasource.xml과 mybatis-config.xml을 한 줄씩 읽으며 연결합니다."
series: "ledger"
chapter: "mybatis"
seriesOrder: 1
---
## 이 글에서 할 일

Ledger의 DAO는 `sqlSession.selectList("...")` 한 줄로 SQL을 실행합니다. 그 한 줄 뒤에서 무엇이 일어나는지, 설정 파일을 따라 아래에서부터 쌓아 올립니다.

```text
DAO ──▶ SqlSessionTemplate ──▶ SqlSessionFactory ──▶ DataSource(HikariCP) ──▶ JDBC 드라이버 ──▶ PostgreSQL
         (MyBatis + Spring)     (매퍼 XML 보관)        (연결 재사용)
```

## 1. JDBC: Java의 DB 표준

**JDBC**는 Java가 DB에 SQL을 보내는 표준 방법입니다. 순수 JDBC로 쓰면 다음과 같습니다.

```java
try (Connection conn = DriverManager.getConnection(url, user, password);
     PreparedStatement ps = conn.prepareStatement("SELECT name FROM category WHERE user_id = ?")) {
    ps.setLong(1, userId);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            names.add(rs.getString("name"));
        }
    }
}
```

연결 열기, SQL 준비, 값 넣기, 결과 한 줄씩 꺼내기, 닫기를 매번 써야 합니다. **MyBatis**는 이 반복을 대신하고, SQL은 XML 파일에 모아 둡니다. **PostgreSQL JDBC 드라이버**(`org.postgresql:postgresql`)는 JDBC 표준을 PostgreSQL에 맞게 구현한 라이브러리입니다.

## 2. DataSource와 HikariCP: 연결 재사용

DB 연결을 여는 데는 시간이 걸립니다(네트워크 왕복, 인증). 요청마다 새로 열고 닫으면 느립니다. **커넥션 풀**은 연결을 미리 몇 개 열어 두고 빌려주고 돌려받습니다. Ledger는 **HikariCP**를 씁니다.

`src/main/resources/spring/context-datasource.xml`

```xml
<bean id="dataSource-postgresql" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">
    <!-- Tomcat 이 DriverManager 를 먼저 초기화해서 웹앱 드라이버가 자동 등록되지 않으므로 명시 -->
    <property name="driverClassName" value="org.postgresql.Driver"/>
    <property name="jdbcUrl" value="${Globals.Ledger.Postgre.Url}"/>
    <property name="username" value="${Globals.Ledger.Postgre.UserName}"/>
    <property name="password" value="${Globals.Ledger.Postgre.Password}"/>
    <property name="maximumPoolSize" value="5"/>
    <property name="connectionTimeout" value="5000"/>
</bean>
```

| 속성 | 뜻 |
|---|---|
| `driverClassName` | 쓸 JDBC 드라이버. 주석의 이유로 직접 적었습니다 |
| `jdbcUrl`, `username`, `password` | 접속 정보. [환경 변수](/posts/ledger-spring-properties)에서 옴 |
| `maximumPoolSize 5` | 연결을 최대 5개까지. 1인 가계부라 작게. 많을수록 PostgreSQL 메모리를 씀 |
| `connectionTimeout 5000` | 연결을 빌리려고 5초 기다려도 없으면 오류. 무한정 기다리며 요청이 쌓이지 않게 |
| `destroy-method="close"` | 앱이 끝날 때 풀의 연결을 모두 닫음 |

## 3. SqlSessionFactory: 매퍼 XML을 읽어 둠

```xml
<bean id="sqlSessionFactory-ledger-postgre" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource-postgresql"/>
    <property name="configLocation" value="classpath:/sqlmap/mybatis-config.xml"/>
    <property name="mapperLocations" value="classpath:/sqlmap/mappers/ledger/**/*.xml"/>
</bean>
```

| 속성 | 뜻 |
|---|---|
| `dataSource` | 위의 HikariCP 풀을 씀 |
| `configLocation` | MyBatis 공통 설정 파일 |
| `mapperLocations` | 매퍼 XML 위치. `**`는 하위 폴더 전부. 기능 폴더를 새로 만들어도 자동으로 읽힘 |

앱이 시작될 때 매퍼 XML을 모두 읽고 검사합니다. **XML 문법이 틀리거나 id가 중복되면 앱이 시작되지 않으므로** 실수를 일찍 발견합니다.

## 4. SqlSessionTemplate: DAO가 쓰는 창구

```xml
<bean id="sqlSession-ledger-postgre" class="org.mybatis.spring.SqlSessionTemplate" destroy-method="clearCache">
    <constructor-arg name="sqlSessionFactory" ref="sqlSessionFactory-ledger-postgre"/>
</bean>
```

`SqlSessionTemplate`은 mybatis-spring이 제공하는 클래스로, DAO가 SQL을 실행하는 창구입니다. 중요한 점은 **Spring 트랜잭션에 자동으로 참여한다**는 것입니다. `@Transactional` 안에서 여러 DAO 메서드를 부르면 모두 같은 DB 연결을 써서 한 트랜잭션이 됩니다([트랜잭션](/posts/ledger-spring-transaction)). 여러 스레드가 함께 써도 안전해서 빈 하나를 모든 DAO가 나눠 씁니다.

빈 이름 `sqlSession-ledger-postgre`는 DAO에서 `@Resource(name = ...)`로 고를 때 씁니다.

## 5. mybatis-config.xml: 공통 설정

`src/main/resources/sqlmap/mybatis-config.xml`

```xml
<configuration>
    <settings>
        <setting name="mapUnderscoreToCamelCase" value="true"/>
        <setting name="jdbcTypeForNull" value="NULL"/>
        <setting name="logImpl" value="SLF4J"/>
        <setting name="callSettersOnNulls" value="true"/>
    </settings>
    <typeAliases>
        <package name="ledger"/>
    </typeAliases>
</configuration>
```

| 설정 | 뜻 |
|---|---|
| `mapUnderscoreToCamelCase` | 결과를 **객체**에 담을 때 `pay_day` 컬럼을 `payDay` 속성으로 연결. Map 결과에는 적용되지 않음([다음 글](/posts/ledger-mybatis-params-results)) |
| `jdbcTypeForNull NULL` | 파라미터가 null일 때 DB에 보낼 타입. 드라이버에 따라 타입을 몰라 오류가 나는 것을 막음 |
| `logImpl SLF4J` | 실행한 SQL을 Logback으로 출력. 로컬은 DEBUG라 SQL이 보임 |
| `callSettersOnNulls` | 값이 null인 컬럼도 Map에 키를 넣음. 꺼지면 null 컬럼은 키 자체가 사라져 화면 JS가 헷갈림 |
| `typeAliases package` | `ledger` 패키지 클래스를 짧은 이름으로 부를 수 있게 |

## 정리

- JDBC는 Java의 DB 표준이고, MyBatis는 그 반복을 대신하며 SQL을 XML에 둡니다.
- HikariCP가 연결을 미리 열어 두고 재사용합니다(최대 5개, 5초 대기).
- `SqlSessionFactory`가 매퍼 XML을 시작할 때 읽고, `SqlSessionTemplate`이 DAO의 창구로 Spring 트랜잭션에 참여합니다.
- `mybatis-config.xml`은 null 처리와 SQL 로그 같은 공통 동작을 정합니다.
