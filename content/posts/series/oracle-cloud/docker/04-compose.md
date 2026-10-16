---
title: "docker compose로 앱 실행하기"
slug: "oracle-cloud-docker-compose"
category: "development"
publishedAt: "2026-10-07"
summary: "docker-compose.yml을 한 줄씩 작성하며 host 네트워크, 비밀값 파일(env_file), Tomcat을 127.0.0.1에만 여는 방법, 볼륨, 재시작 정책과 자주 쓰는 compose 명령을 정리합니다."
series: "oracle-cloud"
chapter: "docker"
seriesOrder: 4
---
## 이 글에서 할 일

`docker run`에 옵션을 길게 붙여 컨테이너를 띄울 수도 있지만, 옵션을 매번 기억하기 어렵습니다. **docker compose**는 실행 옵션을 `docker-compose.yml` 파일에 적어 두고 `docker compose up` 한 줄로 실행합니다. 설정이 파일로 남으니 git으로 관리할 수도 있습니다.

## docker-compose.yml

[앞 글](/posts/oracle-cloud-docker-dockerfile)의 Dockerfile과 같은 폴더에 둡니다.

`docker-compose.yml`

```yaml
name: myapp

services:
  app:
    build:
      context: .
    image: myapp:local
    container_name: myapp
    network_mode: host
    env_file:
      - /etc/myapp/app.env
    environment:
      CATALINA_OPTS: -Dtomcat.address=127.0.0.1
    volumes:
      - /srv/uploads:/data/uploads
    mem_limit: 768m
    restart: unless-stopped
```

| 줄 | 뜻 |
|---|---|
| `name: myapp` | 프로젝트 이름. `docker compose ls`에 이 이름으로 보입니다 |
| `services: app:` | 실행할 서비스(컨테이너) 목록. 여기서는 `app` 하나 |
| `build: context: .` | 이 폴더의 Dockerfile로 이미지를 빌드 |
| `image: myapp:local` | 빌드한 이미지에 붙일 이름 |
| `container_name: myapp` | 컨테이너 이름. `docker logs myapp`처럼 씁니다 |
| `network_mode: host` | 서버의 네트워크를 그대로 함께 씀 (아래 설명) |
| `env_file` | 비밀값이 담긴 파일을 환경 변수로 불러옴 (아래 설명) |
| `environment` | 추가 환경 변수 |
| `volumes` | 서버 폴더를 컨테이너 안에 연결 (아래 설명) |
| `mem_limit: 768m` | 메모리 상한 ([다음 글](/posts/oracle-cloud-docker-memory)) |
| `restart: unless-stopped` | 컨테이너가 죽거나 서버가 재부팅되면 자동으로 다시 시작. 사람이 직접 멈춘 경우만 예외 |

## network_mode: host

보통 Docker 컨테이너는 서버와 분리된 네트워크(bridge)를 가지고, 포트를 연결(`ports: "8080:8080"`)해서 밖과 통신합니다. **host 모드**는 컨테이너가 서버 네트워크를 그대로 씁니다.

| | 기본 (bridge) | host 모드 |
|---|---|---|
| 컨테이너의 `127.0.0.1` | 컨테이너 자신 | **서버 자신** |
| 서버에 직접 설치한 PostgreSQL 접속 | 별도 설정 필요 | `127.0.0.1:5432`로 바로 |
| 포트 연결(`ports:`) | 필요 | 필요 없음 (Tomcat이 서버 포트를 직접 엶) |
| 앱끼리 포트 충돌 | 컨테이너마다 분리 | **같은 서버 포트를 나눠 써야 함** |

PostgreSQL을 서버에 직접 설치한 구성([PostgreSQL 장](/posts/oracle-cloud-postgresql-network-access))에서는 host 모드가 가장 간단합니다. 대신 포트 충돌을 직접 관리해야 하므로, 앱을 추가할 때마다 포트를 정해 표로 적어 둡니다.

bridge 모드를 쓰고 싶다면 compose에 `extra_hosts: ["host.docker.internal:host-gateway"]`를 넣어 컨테이너에서 서버를 `host.docker.internal`이라는 이름으로 부를 수 있게 하고, PostgreSQL이 Docker 네트워크 주소에서도 듣도록 `listen_addresses`와 `pg_hba.conf`를 함께 고쳐야 합니다. 설정할 곳이 늘어나므로 이 시리즈는 host 모드를 씁니다.

## Tomcat을 127.0.0.1에만 열기

```yaml
    environment:
      CATALINA_OPTS: -Dtomcat.address=127.0.0.1
```

[Dockerfile 글](/posts/oracle-cloud-docker-dockerfile)에서 Tomcat의 듣는 주소를 `${tomcat.address}` 변수로 바꿔 두었고, 기본값은 `0.0.0.0`(모든 주소)입니다. 서버에서 실행할 때는 compose가 이 값을 `127.0.0.1`로 덮어씁니다. 그래서 서버에서는 Tomcat이 외부에 열리지 않고, 내 PC에서 테스트할 때는 기본값으로 접속할 수 있습니다.

```bash
sudo ss -ltnp | grep java
```

```text
[::ffff:127.0.0.1]:8080  ... "java"
```

## env_file: 비밀값은 저장소 밖에

DB 비밀번호, API 키 같은 비밀값은 git 저장소에 넣으면 안 됩니다. 비밀값은 **서버의** `/etc/myapp/app.env`에만 두고, compose가 실행할 때 읽어 들입니다. 저장소에는 값을 비운 예시 파일(`deploy/app.env.example`)만 둡니다.

`deploy/app.env.example`

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/myapp
DB_USER=myapp_user
DB_PASSWORD=
```

서버에 실제 파일을 만드는 순서는 다음과 같습니다.

```bash
sudo mkdir -p /etc/myapp
sudo cp deploy/app.env.example /etc/myapp/app.env
sudo nano /etc/myapp/app.env                     # 값 채우기
sudo chown root:docker /etc/myapp/app.env
sudo chmod 640 /etc/myapp/app.env                # root 쓰기, docker 그룹 읽기
```

권한의 의미는 [권한 글](/posts/oracle-cloud-linux-users-permissions)에서 설명했습니다. 앱에서는 `System.getenv("DB_PASSWORD")`처럼 환경 변수로 읽습니다.

env 파일을 고친 뒤에는 `docker compose restart`로는 반영되지 않습니다. 컨테이너를 새로 만들어야 하므로 `docker compose up -d`를 실행합니다.

## volumes: 지워지면 안 되는 파일

컨테이너는 지우면 안의 파일도 함께 사라집니다. 배포할 때마다 컨테이너를 새로 만들기 때문에, 사용자가 올린 사진처럼 남아야 하는 파일은 **서버의 폴더를 컨테이너 안에 연결**(볼륨)합니다.

```yaml
    volumes:
      - /srv/uploads:/data/uploads
```

`서버 경로:컨테이너 경로` 순서입니다. 앱은 `/data/uploads`에 저장하지만 실제 파일은 서버의 `/srv/uploads`에 남습니다. nginx도 이 서버 폴더에서 사진을 직접 보낼 수 있습니다([리버스 프록시](/posts/oracle-cloud-nginx-reverse-proxy) 참고).

## 자주 쓰는 명령

compose 파일이 있는 폴더에서 실행합니다.

```bash
docker compose up -d --build     # 이미지를 빌드하고 백그라운드(-d)로 실행
docker compose ps                # 상태
docker compose logs -f app       # 로그 실시간 보기
docker compose restart           # 재시작 (env 파일을 바꿨다면 up -d로 다시 만들어야 반영)
docker compose down              # 중지하고 컨테이너 삭제 (이미지와 볼륨 폴더는 남음)
docker compose ls                # 서버의 모든 compose 프로젝트
```

## 정리

- compose는 컨테이너 실행 옵션을 `docker-compose.yml`에 적어 두고 `docker compose up -d`로 실행합니다.
- `network_mode: host`로 서버의 PostgreSQL에 `127.0.0.1`로 접속하고, 대신 포트 충돌은 직접 관리합니다.
- `CATALINA_OPTS`로 서버에서만 Tomcat을 `127.0.0.1`에 엽니다.
- 비밀값은 `env_file`로 서버의 파일에서 읽고, 저장소에는 예시만 둡니다.
- 지워지면 안 되는 파일은 볼륨으로 서버 폴더에 연결합니다.
