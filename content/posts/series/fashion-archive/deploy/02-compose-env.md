---
title: "app.env와 Docker Compose로 실행하기"
slug: "fashion-archive-deploy-compose-env"
category: "development"
publishedAt: "2026-10-08"
summary: "서버에 /etc/fashion-archive/app.env를 만들어 DB 비밀번호, 사진 공개 주소, 가입 코드를 넣고, 저장소의 Compose 구성(host 네트워크, 127.0.0.1, 업로드 볼륨, 메모리 상한)으로 실행하는 과정을 설명합니다."
series: "fashion-archive"
chapter: "deploy"
seriesOrder: 2
---
## 이 글에서 할 일

서버에서 Fashion Archive를 Docker Compose로 실행합니다. 저장소에는 비밀값이 없으므로, 서버에 **환경 변수 파일**을 먼저 만들고 Compose가 그 파일을 읽게 합니다.

이 글은 호스트에 PostgreSQL이 있고 `fashion` DB에 `schema.sql`을 적용했으며, 저장소를 `/srv/devgear/fashion-archive`에 받아 둔 Linux 서버를 기준으로 합니다. 서버 자체를 준비하는 과정은 [Oracle Cloud 서버 구축](/series/oracle-cloud) 시리즈에 있습니다.

## 1단계. app.env 만들기

설정 키는 `globals.properties`가 참조하는 환경 변수 이름입니다([로컬 실행](/posts/fashion-archive-intro-local-run)).

```bash
sudo install -d -m 700 /etc/fashion-archive
sudoedit /etc/fashion-archive/app.env
```

`/etc/fashion-archive/app.env`에 넣을 항목

```text
FASHION_DB_URL=jdbc:postgresql://127.0.0.1:5432/fashion
FASHION_DB_USER=앱 DB 계정
FASHION_DB_PASSWORD=그 계정의 비밀번호
FASHION_UPLOAD_BASE_URL=https://도메인/uploads
FASHION_SIGNUP_CODE=비공개 가입 코드
```

| 변수 | 값 |
|---|---|
| `FASHION_DB_URL` | 호스트 PostgreSQL. 컨테이너가 호스트 네트워크를 쓰므로 `127.0.0.1` |
| `FASHION_DB_USER`, `FASHION_DB_PASSWORD` | 앱 전용 DB 계정. 비밀번호는 기본값이 없어 반드시 넣음 |
| `FASHION_UPLOAD_BASE_URL` | nginx가 사진을 보내는 공개 주소 앞부분. 비면 사진 등록이 "저장소 미설정"으로 실패 |
| `FASHION_SIGNUP_CODE` | 가입 코드. 비면 아무도 가입할 수 없음 |

`FASHION_UPLOAD_DIR`은 넣지 않아도 됩니다. 기본값 `/data/fashion-uploads`가 컨테이너 안의 경로이고, 아래 볼륨으로 서버 폴더에 연결됩니다.

운영 파일의 값은 저장소나 문서에 복사하지 않습니다. 가입 코드를 바꾼 뒤에는 `sudo docker compose up -d`로 컨테이너를 다시 만들어야 반영됩니다(`deploy/OPERATIONS.md`).

## 2단계. 사진 폴더

```bash
sudo install -d /srv/fashion-uploads
```

Tomcat이 이 폴더 아래에 사용자별 폴더를 만들고 사진을 씁니다. nginx도 이 폴더를 읽습니다. 폴더와 파일 권한은 앱이 직접 정합니다([디스크 저장](/posts/fashion-archive-upload-local-storage)).

## 3단계. Compose 구성 읽기

`docker-compose.yml`

```yaml
name: fashion-archive

services:
  app:
    build:
      context: .
    image: fashion-archive:local
    container_name: fashion-archive
    network_mode: host
    env_file:
      - /etc/fashion-archive/app.env
    environment:
      CATALINA_OPTS: -Dtomcat.address=127.0.0.1
    volumes:
      - /srv/fashion-uploads:/data/fashion-uploads
    # 힙 384m(Dockerfile -Xmx) + 힙 밖(메타스페이스·스레드·코드 캐시) 여유. 한 앱이 서버 메모리를 다 쓰지 못하게 막는다
    mem_limit: 640m
    restart: unless-stopped
```

| 줄 | 뜻 |
|---|---|
| `build: context: .` | 이 폴더의 Dockerfile로 이미지를 만듦 |
| `network_mode: host` | 컨테이너가 서버의 네트워크를 그대로 씀. `127.0.0.1:5432`가 서버의 PostgreSQL |
| `env_file` | 1단계 파일을 환경 변수로 읽음 |
| `CATALINA_OPTS: -Dtomcat.address=127.0.0.1` | Dockerfile의 기본값(`0.0.0.0`)을 덮어써 Tomcat을 서버 자신에서만 열음 |
| `volumes` | 서버 `/srv/fashion-uploads` ↔ 컨테이너 `/data/fashion-uploads` |
| `mem_limit: 640m` | 컨테이너 메모리 상한. 힙 384MB + 힙 밖 여유 |
| `restart: unless-stopped` | 컨테이너가 죽거나 서버가 재부팅되면 다시 시작 |

### 볼륨이 필요한 이유

배포할 때마다 컨테이너를 새로 만듭니다. 컨테이너 안에만 저장한 파일은 컨테이너와 함께 사라집니다. 사진은 남아야 하므로 서버 폴더를 연결해, 앱이 `/data/fashion-uploads`에 쓰면 실제로는 `/srv/fashion-uploads`에 남게 합니다.

### host 네트워크와 포트

`network_mode: host`라서 `ports:` 설정 없이 Tomcat이 서버의 8080 포트를 직접 엽니다. 같은 서버의 다른 Tomcat 앱과 포트가 겹치지 않아야 합니다. host 모드의 장단점은 [Oracle Cloud 시리즈의 compose 글](/posts/oracle-cloud-docker-compose)에 있습니다.

## 4단계. 빌드하고 실행

`deploy/OPERATIONS.md`의 재배포 순서입니다. 저장소 폴더에서 실행합니다.

```bash
cd /srv/devgear/fashion-archive
mvn test
mvn -q package -DskipTests
sudo docker compose up -d --build
```

Dockerfile이 `target/fashion-archive.war`를 복사하므로 `package`가 먼저입니다. 이 문서의 순서는 테스트를 먼저 따로 돌리고 `package`에서는 건너뜁니다. 자동 배포 스크립트는 `mvn -q package` 하나로 테스트와 war 생성을 함께 합니다([자동 배포](/posts/fashion-archive-deploy-deploy-actions)).

## 5단계. 확인

```bash
sudo docker compose ps
sudo docker compose logs -f app
sudo ss -ltnp | grep ':8080 '
```

| 확인 | 기대 |
|---|---|
| `compose ps` | `fashion-archive`가 실행 중 |
| `logs` | Tomcat 시작 로그, Spring 오류 없음 |
| `ss` | `127.0.0.1:8080`에서 `java`가 들음 (`0.0.0.0`이 아님) |

브라우저 확인은 [nginx 연결](/posts/fashion-archive-deploy-nginx) 뒤에 `https://도메인/p1/`로 합니다. 로그인 화면이 나오면 정상입니다.

## 정리

- 서버의 `/etc/fashion-archive/app.env`에 DB 접속, 사진 공개 주소, 가입 코드를 넣고 Compose의 `env_file`로 읽습니다.
- 사진은 볼륨으로 서버 폴더에 남겨 컨테이너를 새로 만들어도 유지합니다.
- 서버에서는 `CATALINA_OPTS`로 Tomcat을 `127.0.0.1`에만 열고, 메모리를 640MB로 제한합니다.
- war를 먼저 만들고 `docker compose up -d --build`로 실행합니다.
