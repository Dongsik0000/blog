---
title: "Dockerfile로 WAR를 만들고 Tomcat에서 실행하기"
slug: "ledger-deploy-dockerfile"
category: "development"
publishedAt: "2026-10-08"
summary: "완성된 Dockerfile의 빌드 단계와 실행 단계를 읽고, p3.war 배포 경로, 포트와 종료 포트, 메모리와 시간대, 상태 확인을 설명합니다."
series: "ledger"
chapter: "deploy"
seriesOrder: 2
---
## 이 글에서 할 일

Ledger의 Dockerfile은 Maven으로 WAR를 만든 뒤 Tomcat 이미지에 넣습니다. **WAR**는 Java 웹 앱의 코드, JSP, 설정, 라이브러리를 묶은 배포 파일입니다. 서버에서 별도로 Maven과 Tomcat을 설치하지 않고 이 이미지로 앱을 실행합니다.

## 빌드 단계

`Dockerfile` (빌드 단계)

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests
```

1. Maven과 Java 17이 있는 이미지에서 `/build`를 작업 위치로 정합니다.
2. `pom.xml`을 먼저 복사해 의존성을 받습니다. 소스만 바뀌면 이 단계의 빌드 캐시를 재사용할 수 있습니다.
3. `src/`를 복사하고 `target/ledger.war`를 만듭니다.

현재 파일의 `-DskipTests`는 테스트 실행을 생략합니다. 테스트 코드는 저장소에 있으며, [테스트 실행 글](/posts/ledger-test-existing-tests)의 `mvn test`로 결과를 확인합니다. Docker 빌드 성공과 테스트 통과는 별도로 확인할 항목입니다.

## 실행 단계와 포트

`Dockerfile` (Tomcat 설정 부분)

```dockerfile
FROM tomcat:10.1-jdk17-temurin

ENV CATALINA_OPTS="-Dtomcat.address=0.0.0.0"
RUN sed -i -e 's/<Connector port="8080"/<Connector address="${tomcat.address}" port="8081"/' \
           -e 's/<Server port="8005"/<Server port="-1"/' /usr/local/tomcat/conf/server.xml \
    && grep -q '<Server port="-1"' /usr/local/tomcat/conf/server.xml \
    && grep -q 'port="8081"' /usr/local/tomcat/conf/server.xml
```

HTTP 포트는 8081로 바꿉니다. 주소는 `tomcat.address`라는 Java 시스템 속성에서 읽고, 운영 Compose가 이를 `127.0.0.1`로 지정합니다.

8005는 웹 요청을 받는 포트가 아니라 Tomcat 종료 명령용 포트입니다. `-1`로 꺼서 같은 서버의 다른 Tomcat과 충돌하지 않게 합니다. 컨테이너 종료는 Docker가 보내는 신호로 처리합니다.

## `/p3` 경로로 배포하기

`Dockerfile` (웹 앱 복사 부분)

```dockerfile
RUN rm -rf /usr/local/tomcat/webapps/*

COPY src/main/webapp/META-INF/context.xml /usr/local/tomcat/conf/Catalina/localhost/p3.xml
COPY --from=build /build/target/ledger.war /usr/local/tomcat/webapps/p3.war
```

Maven의 결과물 이름은 `ledger.war`지만, Tomcat에는 `p3.war`로 넣습니다. 이 이름에 맞춰 앱의 주소가 `/p3/`가 됩니다. 쿠키 설정도 같은 컨텍스트 이름인 `p3.xml`로 복사합니다.

앞 단계의 컴파일 도구를 최종 이미지로 옮기지 않고 WAR만 가져오는 방식입니다. 기본 웹 앱은 제거하고 Ledger만 배포합니다.

## 시간대와 운영 기본값

`Dockerfile` (환경 설정과 상태 확인)

```dockerfile
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Seoul -Djava.security.egd=file:/dev/./urandom"

ENV LEDGER_LOG_LEVEL=INFO \
    LEDGER_SIGNUP_CODE=""

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8081/p3/manifest.json > /dev/null || exit 1

CMD ["catalina.sh", "run"]
```

Java의 최대 힙은 512MB로 정합니다. 힙은 객체를 저장하는 메모리 영역이며, Compose의 컨테이너 전체 메모리 제한 768MB에는 힙 밖의 사용량도 포함됩니다.

시간대는 한국 시간으로 맞춰 [매일 실행되는 스케줄러](/posts/ledger-domain-scheduler)가 같은 기준을 사용하게 합니다. 운영 로그 기본값은 `INFO`이며, 가입 코드는 `/etc/ledger/app.env`에서 입력합니다.

상태 확인은 `/p3/manifest.json`을 호출합니다. 로그인 화면은 세션을 만들 수 있으므로, 정적 파일로 웹 앱의 응답을 확인합니다.

## 확인 방법

이미지 빌드와 실행은 다음 [Compose와 app.env 글](/posts/ledger-deploy-compose-env)에서 진행합니다. 실행 후 `healthy`는 위 정적 파일이 응답한다는 뜻이며, 로그인과 DB 조회도 별도로 확인합니다.
