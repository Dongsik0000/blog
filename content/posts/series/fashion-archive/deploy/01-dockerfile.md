---
title: "Dockerfile (war를 복사하는 Tomcat 9 이미지)"
slug: "fashion-archive-deploy-dockerfile"
category: "development"
publishedAt: "2026-10-08"
summary: "미리 빌드한 war를 Tomcat 9 이미지에 넣는 단일 스테이지 Dockerfile을 한 줄씩 읽으며, Tomcat 듣는 주소를 변수로 바꾸는 sed, /p1 컨텍스트와 SameSite 설정 파일, 기본 웹앱 제거, 힙 상한을 설명합니다."
series: "fashion-archive"
chapter: "deploy"
seriesOrder: 1
---
## 이 글에서 할 일

Fashion Archive의 `Dockerfile`은 짧습니다. Maven 빌드는 이미지 밖(서버)에서 먼저 하고, 이미지는 만들어진 war를 Tomcat에 넣기만 합니다. 한 줄씩 읽습니다.

## 전체

`Dockerfile`

```dockerfile
FROM tomcat:9-jdk17
# 서버에서는 Tomcat을 loopback에만 열고, 로컬 Docker에서는 기본값(0.0.0.0)으로 포트 매핑할 수 있게 한다.
ENV CATALINA_OPTS="-Dtomcat.address=0.0.0.0"
RUN sed -i 's/<Connector port="8080"/<Connector address="${tomcat.address}" port="8080"/' /usr/local/tomcat/conf/server.xml
# SameSite 쿠키 설정을 /p1 컨텍스트에 적용 (nginx가 /p1/ 하위 경로로 리버스 프록시하므로 ROOT가 아닌 p1 컨텍스트로 배포한다)
COPY src/main/webapp/META-INF/context.xml /usr/local/tomcat/conf/Catalina/localhost/p1.xml
RUN rm -rf /usr/local/tomcat/webapps/*
COPY target/fashion-archive.war /usr/local/tomcat/webapps/p1.war
# 힙 상한을 정하지 않으면 JVM이 서버 메모리의 1/4(약 2.4GB)까지 키운다. 한 서버에 여러 앱을 띄우므로 고정한다
ENV JAVA_OPTS="-Xms128m -Xmx384m"
EXPOSE 8080
```

## 1. 기반 이미지

```dockerfile
FROM tomcat:9-jdk17
```

Tomcat 9와 JDK 17이 들어 있는 공식 이미지입니다. eGovFrame 4.1의 Spring 5.3은 `javax.servlet` 기반이라 Tomcat 9를 씁니다([javax와 Tomcat 9](/posts/fashion-archive-egovframe-javax-tomcat9)).

## 2. Tomcat이 듣는 주소를 변수로

```dockerfile
ENV CATALINA_OPTS="-Dtomcat.address=0.0.0.0"
RUN sed -i 's/<Connector port="8080"/<Connector address="${tomcat.address}" port="8080"/' /usr/local/tomcat/conf/server.xml
```

Tomcat 설정 파일 `server.xml`의 Connector(요청을 받는 부분)에 `address="${tomcat.address}"`를 끼워 넣습니다. Tomcat은 `${...}`를 Java 시스템 속성 값으로 바꿔 읽습니다.

| 실행 환경 | `tomcat.address` | 결과 |
|---|---|---|
| 이미지 기본값 (로컬 Docker) | `0.0.0.0` | 모든 주소에서 받음. 포트를 연결해 PC 브라우저로 접속 |
| 서버 (compose가 덮어씀) | `127.0.0.1` | 서버 자신에서만 받음. 외부 요청은 nginx를 거침 |

같은 이미지를 로컬과 서버에서 함께 쓰되, 서버에서만 Tomcat을 외부에 열지 않게 합니다. 서버 쪽 값은 [compose 글](/posts/fashion-archive-deploy-compose-env)에서 봅니다. `-D` 옵션을 `CATALINA_OPTS`에 담는 것은 Tomcat 시작 스크립트가 이 환경 변수를 JVM 옵션으로 넘기기 때문입니다.

## 3. /p1 컨텍스트와 SameSite

```dockerfile
COPY src/main/webapp/META-INF/context.xml /usr/local/tomcat/conf/Catalina/localhost/p1.xml
RUN rm -rf /usr/local/tomcat/webapps/*
COPY target/fashion-archive.war /usr/local/tomcat/webapps/p1.war
```

| 줄 | 하는 일 |
|---|---|
| `context.xml` → `conf/Catalina/localhost/p1.xml` | `p1` 웹 앱의 설정. 세션 쿠키에 `SameSite=Lax`를 붙임 ([CSRF와 SameSite](/posts/fashion-archive-auth-csrf-url)) |
| `rm -rf webapps/*` | 이미지에 들어 있을 수 있는 기본 웹앱을 지움 |
| war → `webapps/p1.war` | 파일 이름 `p1`이 컨텍스트 경로 `/p1`이 됨 |

nginx가 `https://도메인/p1/`을 `127.0.0.1:8080`으로 그대로 넘기므로(경로를 바꾸지 않음), Tomcat 안에서도 앱이 `/p1`에 있어야 주소가 맞습니다([nginx 연결](/posts/fashion-archive-deploy-nginx)).

## 4. 메모리 상한

```dockerfile
# 힙 상한을 정하지 않으면 JVM이 서버 메모리의 1/4(약 2.4GB)까지 키운다. 한 서버에 여러 앱을 띄우므로 고정한다
ENV JAVA_OPTS="-Xms128m -Xmx384m"
```

Java 힙을 시작 128MB, 최대 384MB로 고정합니다. 컨테이너 전체 상한(`mem_limit 640m`)은 compose에 있고, 둘의 관계는 [Oracle Cloud 시리즈의 메모리 상한 글](/posts/oracle-cloud-docker-memory)에서 설명합니다.

## war는 이미지 밖에서 만든다

```dockerfile
COPY target/fashion-archive.war /usr/local/tomcat/webapps/p1.war
```

이미지 안에서 Maven을 실행하지 않고, **이미 만들어진** `target/fashion-archive.war`를 복사합니다. 그래서 이미지를 빌드하기 전에 `mvn package`가 끝나 있어야 합니다. 배포 스크립트가 이 순서를 지킵니다(커밋 `38e9880` "자동 배포에 Maven 빌드 추가: Dockerfile이 target/fashion-archive.war를 복사하므로 compose 전에 package 필요").

```bash
mvn -q package             # target/fashion-archive.war 생성 (테스트 포함)
docker compose up -d --build
```

`.dockerignore`는 이미지 빌드에 보낼 파일을 정합니다.

```text
.git
.idea
docs
src/test
target/*
!target/fashion-archive.war
```

`target/` 아래에서는 war 하나만 보내고(`!`는 예외), 테스트 코드와 문서, git 기록은 보내지 않습니다. 빌드 방식의 차이는 [Ledger와 비교](/posts/fashion-archive-egovframe-vs-ledger)에 정리했습니다.

## 정리

- `tomcat:9-jdk17` 이미지에 미리 빌드한 war를 `p1.war`로 넣어 `/p1`에서 실행합니다.
- `sed`로 Tomcat 듣는 주소를 `${tomcat.address}` 변수로 바꾸고, 서버에서는 `127.0.0.1`로 덮어씁니다.
- `context.xml`을 `p1.xml`로 복사해 `SameSite` 쿠키 설정을 적용합니다.
- 힙 상한을 384MB로 고정하고, war는 이미지 빌드 전에 `mvn package`로 만듭니다.
