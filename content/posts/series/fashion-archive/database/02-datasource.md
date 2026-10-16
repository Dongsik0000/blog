---
title: "commons-dbcp2와 SqlSessionTemplate"
slug: "fashion-archive-database-datasource"
category: "development"
publishedAt: "2026-10-08"
summary: "commons-dbcp2의 BasicDataSource로 DB 연결을 재사용하고, SqlSessionFactoryBean과 SqlSessionTemplate으로 MyBatis를 Spring에 연결하는 설정, mybatis-config.xml의 null 처리와 SQL 로그 설정을 설명합니다."
series: "fashion-archive"
chapter: "database"
seriesOrder: 2
---
## 이 글에서 할 일

DAO의 `sqlSession.selectList(...)` 한 줄 뒤에서 무엇이 일어나는지, `context-datasource.xml`과 `mybatis-config.xml`을 따라갑니다.

```text
DAO ──▶ SqlSessionTemplate ──▶ SqlSessionFactory ──▶ BasicDataSource(dbcp2) ──▶ PostgreSQL
                                (매퍼 XML 보관)          (연결 재사용)
```

## 1. 연결 풀: commons-dbcp2

DB 연결을 여는 데는 시간이 걸리므로, 연결을 미리 열어 두고 빌려주는 **커넥션 풀**을 씁니다.

`src/main/resources/spring/context-datasource.xml`

```xml
<bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource" destroy-method="close">
    <property name="driverClassName" value="org.postgresql.Driver" />
    <property name="url" value="${Globals.Fashion.Postgre.Url}" />
    <property name="username" value="${Globals.Fashion.Postgre.UserName}" />
    <property name="password" value="${Globals.Fashion.Postgre.Password}" />
    <property name="maxTotal" value="5" />
</bean>
```

| 속성 | 뜻 |
|---|---|
| `driverClassName` | PostgreSQL JDBC 드라이버 |
| `url`, `username`, `password` | 접속 정보. `globals.properties`를 거쳐 환경 변수에서 옴 |
| `maxTotal 5` | 동시에 쓸 수 있는 연결 최대 5개 |
| `destroy-method="close"` | 앱이 끝날 때 열어 둔 연결을 모두 닫음 |

commons-dbcp2는 eGovFrame 데이터처리 모듈(`psl.dataaccess`)의 POM에 test 범위로만 있어서, `pom.xml`에 직접 선언했습니다([Maven 의존성](/posts/fashion-archive-egovframe-maven)).

## 2. SqlSessionFactory: 매퍼 XML 읽기

```xml
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource" />
    <property name="configLocation" value="classpath:/sqlmap/mybatis-config.xml" />
    <property name="mapperLocations" value="classpath:/sqlmap/mappers/**/*.xml" />
</bean>
```

| 속성 | 뜻 |
|---|---|
| `dataSource` | 위의 연결 풀 |
| `configLocation` | MyBatis 공통 설정 |
| `mapperLocations` | 매퍼 XML 위치. `**`는 하위 폴더 전부 (`fashion/cmmn/`, `fashion/photo/`) |

앱이 시작될 때 매퍼 XML을 모두 읽습니다. XML 문법이 틀리면 앱이 시작되지 않아 실수를 일찍 알 수 있습니다.

## 3. SqlSessionTemplate: DAO의 창구

```xml
<bean id="sqlSession" class="org.mybatis.spring.SqlSessionTemplate" destroy-method="clearCache">
    <constructor-arg name="sqlSessionFactory" ref="sqlSessionFactory" />
</bean>
```

mybatis-spring이 제공하는 클래스로, DAO가 SQL을 실행하는 창구입니다.

- **Spring 트랜잭션에 참여합니다.** `TransactionTemplate.execute(...)` 안에서 DAO 메서드를 여러 번 불러도 같은 DB 연결을 써서 한 트랜잭션이 됩니다.
- 여러 요청이 동시에 써도 안전해서 빈 하나를 모든 DAO가 나눠 씁니다.

DAO는 빈 이름 `sqlSession`으로 주입받습니다.

```java
@Resource(name = "sqlSession")
private SqlSessionTemplate sqlSession;
```

## 4. mybatis-config.xml

`src/main/resources/sqlmap/mybatis-config.xml`

```xml
<configuration>
    <settings>
        <setting name="jdbcTypeForNull" value="NULL" />
        <setting name="logImpl" value="LOG4J2" />
        <setting name="callSettersOnNulls" value="true" />
    </settings>
</configuration>
```

| 설정 | 뜻 |
|---|---|
| `jdbcTypeForNull NULL` | 파라미터가 `null`일 때 DB에 보낼 타입. 메모처럼 비어 있을 수 있는 값을 넣을 때 필요 |
| `logImpl LOG4J2` | 실행한 SQL을 log4j2로 출력 |
| `callSettersOnNulls` | 값이 `null`인 컬럼도 결과 Map에 키를 넣음. 꺼져 있으면 메모가 없는 사진은 `memo` 키 자체가 없음 |

### SQL 로그

`src/main/resources/log4j2.xml` (일부)

```xml
<!-- 매퍼 namespace가 com.fashion.* 이므로 이 설정으로 SQL이 찍힌다 -->
<Logger name="com.fashion" level="DEBUG" additivity="false">
    <AppenderRef ref="console" />
    <AppenderRef ref="file" />
</Logger>
```

MyBatis는 SQL 로그를 **매퍼 namespace 이름**의 로거로 남깁니다. namespace가 `com.fashion.photo.dao.PhotoDAO`처럼 `com.fashion`으로 시작하므로, `com.fashion`을 DEBUG로 두면 실행한 SQL과 파라미터가 보입니다([로그](/posts/fashion-archive-deploy-logs)).

### 결과 Map의 키는 컬럼 이름 그대로

`mapUnderscoreToCamelCase` 설정이 없고 결과를 `HashMap`으로 받으므로, 키는 컬럼 이름 그대로입니다.

```xml
SELECT id, owner_id, title, memo, image_url, created_at FROM photo ...
```

```json
{"id": 15, "owner_id": 1, "title": "...", "image_url": "...", "created_at": 1790000000000}
```

화면 JS도 이 이름(`p.image_url`, `link.item_label`)을 그대로 씁니다. `created_at`은 Jackson이 시각을 숫자(epoch 밀리초)로 내보내므로, 화면 JS가 `new Date(value)`로 바꿔 표시합니다.

반대로 **파라미터 Map의 키**는 Java 쪽에서 정하므로 `ownerId`, `imageUrl`처럼 camelCase를 씁니다.

```xml
INSERT INTO photo (owner_id, title, memo, image_url)
VALUES (#{ownerId}, #{title}, #{memo}, #{imageUrl})
```

## 정리

- commons-dbcp2 `BasicDataSource`가 연결을 최대 5개까지 재사용합니다.
- `SqlSessionFactoryBean`이 매퍼 XML을 시작할 때 읽고, `SqlSessionTemplate`이 Spring 트랜잭션에 참여하는 DAO 창구가 됩니다.
- `callSettersOnNulls`로 null 컬럼도 키를 남기고, SQL 로그는 매퍼 namespace(`com.fashion.*`) 로거로 나옵니다.
- 결과 Map 키는 컬럼 이름(snake_case), 파라미터 키는 Java에서 정한 이름(camelCase)입니다.
