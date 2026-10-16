---
title: "Java 웹 앱용 Dockerfile 작성하기"
slug: "oracle-cloud-docker-dockerfile"
category: "development"
publishedAt: "2026-10-07"
summary: "Spring(war) 앱을 Tomcat 이미지로 포장하는 Dockerfile을 멀티 스테이지와 단일 스테이지 두 방식으로 작성하며 FROM, COPY, RUN, ENV, HEALTHCHECK와 레이어 캐시를 이해합니다."
series: "oracle-cloud"
chapter: "docker"
seriesOrder: 3
---
## 이 글에서 할 일

Maven으로 빌드하는 Java 웹 앱(war)을 Tomcat 컨테이너로 실행하는 Dockerfile을 작성합니다. 빌드를 어디서 하느냐에 따라 두 방식이 있어 함께 비교합니다.

| | 멀티 스테이지 | 단일 스테이지 |
|---|---|---|
| Maven 빌드 위치 | 컨테이너 안 (이미지를 만들 때) | 서버에서 먼저 (`mvn package`) |
| 서버에 필요한 것 | Docker만 | Docker + JDK + Maven |

예시 앱의 이름은 `myapp`이고, 빌드하면 `target/myapp.war`가 생기며, `/shop` 경로로 서비스합니다([리버스 프록시 글](/posts/oracle-cloud-nginx-reverse-proxy)과 같은 구성).

## 주요 명령

| 명령 | 뜻 |
|---|---|
| `FROM` | 어떤 이미지에서 시작할지. 공식 이미지를 기반으로 쌓습니다 |
| `WORKDIR` | 이후 명령을 실행할 폴더 |
| `COPY` | 서버(빌드 폴더)의 파일을 이미지 안으로 복사 |
| `RUN` | 이미지를 만드는 중에 명령 실행 (설치, 빌드, 설정 수정) |
| `ENV` | 환경 변수 설정 |
| `EXPOSE` | 이 컨테이너가 쓰는 포트를 문서로 표시 (실제로 열지는 않음) |
| `HEALTHCHECK` | 컨테이너가 정상인지 주기적으로 검사하는 명령 |
| `CMD` | 컨테이너가 시작될 때 실행할 명령 |

## 방식 1. 멀티 스테이지

`Dockerfile` (저장소 맨 위)

```dockerfile
# ---- 빌드 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- 실행 ----
FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /build/target/myapp.war /usr/local/tomcat/webapps/shop.war
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Seoul"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/shop/css/style.css > /dev/null || exit 1
CMD ["catalina.sh", "run"]
```

`FROM`이 두 번 나옵니다. 첫 번째 단계(`AS build`)는 **빌드 전용**으로, Maven과 JDK가 들어 있는 이미지에서 소스를 war 파일로 만듭니다. 두 번째 단계는 **실행용**으로, `COPY --from=build`로 war 파일만 가져옵니다. Maven과 소스 코드는 최종 이미지에 들어가지 않아 **이미지가 작고 안전합니다.** 이것이 멀티 스테이지의 핵심입니다.

### 레이어 캐시: 왜 pom.xml을 먼저 복사하나

Dockerfile의 각 줄은 **레이어**(층)가 되고, Docker는 바뀌지 않은 레이어를 다시 만들지 않고 재사용합니다(캐시). 한 줄이 바뀌면 그 줄과 **그 아래 줄은 모두** 다시 실행됩니다.

```text
COPY pom.xml .                     ← pom.xml이 그대로면 캐시
RUN mvn dependency:go-offline      ← 위가 캐시면 이것도 캐시 (의존성 다운로드 생략)
COPY src ./src                     ← 소스가 바뀌면 여기서부터 다시
RUN mvn clean package              ← 빌드만 다시
```

소스 코드는 자주 바뀌지만 의존성(`pom.xml`)은 가끔 바뀝니다. 의존성 다운로드를 먼저 해 두면, 코드만 고쳤을 때 수백 MB의 다운로드를 건너뛸 수 있습니다. `COPY . .`로 한 번에 복사하면 파일 하나만 바뀌어도 의존성을 매번 다시 받습니다.

### 실행 단계의 줄들

| 줄 | 뜻 |
|---|---|
| `RUN rm -rf /usr/local/tomcat/webapps/*` | Tomcat 이미지에 기본으로 들어 있을 수 있는 예제 앱을 지웁니다. 운영에 필요 없고 공격받을 곳만 늘립니다 |
| `COPY ... webapps/shop.war` | war 파일 이름이 곧 앱 경로입니다. `shop.war`는 `/shop`으로 서비스됩니다 |
| `TZ`, `-Duser.timezone` | 앱의 시간과 스케줄러가 한국 시간 기준으로 돌게 합니다 |
| `-Xms256m -Xmx512m` | Java 힙 메모리 시작값과 최댓값 ([메모리 상한](/posts/oracle-cloud-docker-memory) 참고) |

### HEALTHCHECK

- 30초마다 앱 안의 정적 파일(`css/style.css`, 앱에 실제로 있는 파일로 바꿉니다)을 요청해 정상인지 검사합니다. 3번 연속 실패하면 `unhealthy`가 됩니다.
- `--start-period=90s`: Tomcat이 뜨는 동안(90초)은 실패해도 셈하지 않습니다.
- 검사 주소는 가볍게 응답하는 곳으로 고릅니다. 로그인 화면처럼 요청마다 세션을 만드는 주소를 30초마다 부르면 세션이 계속 쌓입니다. 예시처럼 앱에 포함된 정적 파일이나, 상태 확인용으로 만든 가벼운 주소가 좋습니다.

```bash
docker inspect myapp --format '{{.State.Health.Status}}'
```

`healthy`가 나오면 정상입니다. `starting`은 아직 시작 중, `unhealthy`는 검사가 계속 실패하는 상태입니다.

## 방식 2. 단일 스테이지

서버에서 먼저 `mvn package`로 war를 만들고, Dockerfile은 그 파일을 복사만 합니다.

```dockerfile
FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY target/myapp.war /usr/local/tomcat/webapps/shop.war
ENV JAVA_OPTS="-Xms128m -Xmx384m"
EXPOSE 8080
```

```bash
mvn -q package                 # 서버에서 war 빌드
docker compose up -d --build   # 이미지 만들고 실행
```

| | 멀티 스테이지 | 단일 스테이지 |
|---|---|---|
| 서버에 필요한 것 | Docker만 | Docker + JDK + Maven |
| 빌드 환경 | 이미지에 고정되어 어디서나 같음 | 서버의 Maven, JDK 버전에 따라 달라질 수 있음 |
| Dockerfile | 조금 김 | 짧음 |

처음 시작한다면 서버에 설치할 것이 적고 결과가 일정한 **멀티 스테이지**를 권합니다.

## Tomcat 설정 바꾸기: 듣는 주소와 포트

Tomcat은 기본적으로 모든 주소(`0.0.0.0`)의 8080번에서 듣습니다. [host 네트워크](/posts/oracle-cloud-docker-compose)로 실행하면 이 포트가 서버에 그대로 열리므로, 서버에서는 `127.0.0.1`에서만 듣게 하는 것이 안전합니다. Tomcat 설정 파일(`server.xml`)을 `RUN sed`로 고칩니다.

```dockerfile
ENV CATALINA_OPTS="-Dtomcat.address=0.0.0.0"
RUN sed -i 's/<Connector port="8080"/<Connector address="${tomcat.address}" port="8080"/' \
        /usr/local/tomcat/conf/server.xml \
    && grep -q 'address="${tomcat.address}"' /usr/local/tomcat/conf/server.xml
```

- `sed`로 Connector에 `address="${tomcat.address}"`를 넣습니다. `${...}`는 Tomcat이 Java 시스템 속성 값으로 바꿔 읽습니다.
- 기본값은 `CATALINA_OPTS`의 `0.0.0.0`이라 내 PC에서 테스트할 때는 그대로 접속됩니다. 서버에서는 compose가 이 값을 `127.0.0.1`로 덮어씁니다.
- 뒤의 `grep -q`는 **수정이 정말 됐는지 확인**합니다. 나중에 Tomcat 이미지가 바뀌어 `sed`가 아무것도 못 바꾸면 빌드가 실패해 바로 알 수 있습니다.

host 네트워크에서 Tomcat 앱 두 개를 함께 띄운다면 포트가 겹치지 않게 두 번째 앱은 포트를 바꿉니다(예: `port="8081"`). Tomcat의 종료 명령 포트(`<Server port="8005"`)도 겹치므로, 쓰지 않는다면 `port="-1"`로 꺼 둡니다.

## 정리

- `FROM`으로 공식 이미지를 기반으로 시작하고, `COPY`, `RUN`, `ENV`로 쌓습니다.
- 자주 안 바뀌는 것(`pom.xml`)을 먼저 복사하면 레이어 캐시로 빌드가 빨라집니다.
- 멀티 스테이지는 빌드 도구를 최종 이미지에서 빼서 작고 안전한 이미지를 만듭니다.
- war 파일 이름이 Tomcat 앱 경로가 됩니다.
- `HEALTHCHECK`로 앱이 실제로 응답하는지 검사합니다.
