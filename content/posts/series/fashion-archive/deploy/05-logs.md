---
title: "log4j2로 로그 남기기"
slug: "fashion-archive-deploy-logs"
category: "development"
publishedAt: "2026-10-08"
summary: "log4j2.xml의 콘솔 출력과 날짜별 파일 출력, Tomcat 로그 폴더를 찾는 catalina.base 속성과 기본값, com.fashion 로거로 SQL을 보는 방법, 컨테이너 로그를 읽는 명령을 설명합니다."
series: "fashion-archive"
chapter: "deploy"
seriesOrder: 5
---
## 이 글에서 할 일

Fashion Archive의 로그 라이브러리는 eGovFrame 실행환경이 가져오는 **log4j2**입니다([Maven 의존성](/posts/fashion-archive-egovframe-maven)). 설정은 `src/main/resources/log4j2.xml` 한 파일입니다.

## 코드에서 로그 쓰기

```java
private static final Logger logger = LoggerFactory.getLogger(PhotoApiController.class);
...
logger.error("사진 등록 중 오류", e);
```

코드는 SLF4J(`org.slf4j.Logger`)로 로그를 쓰고, 실행환경의 `log4j-slf4j-impl`이 그것을 log4j2로 넘깁니다. 코드가 로그 라이브러리에 직접 묶이지 않습니다.

## 설정

`src/main/resources/log4j2.xml`

```xml
<Configuration status="WARN">
    <Appenders>
        <Console name="console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n" />
        </Console>
        <!-- Tomcat logs/ 아래에 앱 로그를 남긴다 (catalina.base 기준) -->
        <RollingFile name="file" fileName="${sys:catalina.base:-target}/logs/fashion-app.log"
                     filePattern="${sys:catalina.base:-target}/logs/fashion-app.%d{yyyy-MM-dd}.log">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{36} - %msg%n" />
            <Policies>
                <TimeBasedTriggeringPolicy />
            </Policies>
            <DefaultRolloverStrategy max="14" />
        </RollingFile>
    </Appenders>
    <Loggers>
        <!-- 매퍼 namespace가 com.fashion.* 이므로 이 설정으로 SQL이 찍힌다 -->
        <Logger name="com.fashion" level="DEBUG" additivity="false">
            <AppenderRef ref="console" />
            <AppenderRef ref="file" />
        </Logger>
        <Root level="INFO">
            <AppenderRef ref="console" />
            <AppenderRef ref="file" />
        </Root>
    </Loggers>
</Configuration>
```

### Appender: 어디에 쓰나

| 이름 | 종류 | 쓰는 곳 |
|---|---|---|
| `console` | `Console` | 표준 출력. Docker가 모아 `docker compose logs`로 보임 |
| `file` | `RollingFile` | `{Tomcat 폴더}/logs/fashion-app.log`. 날짜가 바뀌면 `fashion-app.2026-10-07.log`처럼 넘기고 최근 것만 보관 |

| 설정 | 뜻 |
|---|---|
| `TimeBasedTriggeringPolicy` | `filePattern`의 날짜 단위(하루)로 새 파일 |
| `DefaultRolloverStrategy max="14"` | 넘긴 파일을 최대 14개까지 |

### 파일 위치: catalina.base

```xml
fileName="${sys:catalina.base:-target}/logs/fashion-app.log"
```

| 부분 | 뜻 |
|---|---|
| `${sys:catalina.base}` | Java 시스템 속성 `catalina.base`. Tomcat이 시작할 때 자기 폴더로 정함 |
| `:-target` | 값이 없으면 `target`을 씀 |

Tomcat에서 실행하면 Tomcat의 `logs/` 폴더(catalina.out과 같은 곳)에 앱 로그가 생깁니다. Tomcat 밖(테스트 실행 등)에서는 `catalina.base`가 없으므로 프로젝트의 `target/logs/`에 씁니다(커밋 `1b9c901` "log file path falls back to target/ outside tomcat").

### Logger: 무엇을 얼마나

| 로거 | 수준 | 나오는 것 |
|---|---|---|
| `com.fashion` | DEBUG | 앱 코드의 로그와 **실행한 SQL** |
| Root (그 밖 전부) | INFO | Spring, Tomcat 등 라이브러리의 정보 이상 |

MyBatis는 SQL 로그를 매퍼 namespace 이름의 로거로 남깁니다. namespace가 `com.fashion.photo.dao.PhotoDAO`처럼 `com.fashion`으로 시작하므로, `com.fashion`을 DEBUG로 두면 SQL과 파라미터가 보입니다([commons-dbcp2와 SqlSessionTemplate](/posts/fashion-archive-database-datasource)).

사진 목록을 조회하면 다음과 같은 형태로 찍힙니다.

```text
10:21:05.312 DEBUG com.fashion.photo.dao.PhotoDAO.selectPhotoList - ==>  Preparing: SELECT p.id, ... WHERE p.owner_id = ? ...
10:21:05.314 DEBUG com.fashion.photo.dao.PhotoDAO.selectPhotoList - ==> Parameters: 1(Long)
```

`additivity="false"`는 `com.fashion` 로그가 Root에도 한 번 더 전달되어 두 번 찍히는 것을 막습니다.

## 서버에서 로그 보기

`deploy/OPERATIONS.md`의 명령입니다. 저장소 폴더에서 실행합니다.

```bash
sudo docker compose logs -f app
```

콘솔 출력(`console` appender)이 실시간으로 나옵니다. 사진 등록이 실패했다면 API가 남긴 `logger.error("사진 등록 중 오류", e)`의 스택 트레이스를 여기서 찾습니다. 사용자에게는 "사진을 올리지 못했습니다"만 보이고 원인은 로그에만 남습니다([입력 검증과 오류 응답](/posts/fashion-archive-spring-mvc-validation)).

파일 로그(`fashion-app.log`)는 컨테이너 안의 Tomcat 폴더에 있습니다. 서버의 로그 위치를 정리한 내용은 [Oracle Cloud 시리즈의 로그 글](/posts/oracle-cloud-ops-logs)에 있습니다.

## 정리

- 코드는 SLF4J로 쓰고, eGovFrame 실행환경의 log4j2가 출력합니다.
- 콘솔과 날짜별 파일(14개 보관) 두 곳에 쓰고, 파일 위치는 `catalina.base`가 없으면 `target/`으로 바뀝니다.
- `com.fashion`을 DEBUG로 두어 매퍼 namespace 로거의 SQL까지 보고, 나머지는 INFO입니다.
- 서버에서는 `docker compose logs -f app`으로 봅니다.
