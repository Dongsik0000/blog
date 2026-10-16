---
title: "app.env를 준비하고 Docker Compose로 실행하기"
slug: "ledger-deploy-compose-env"
category: "development"
publishedAt: "2026-10-08"
summary: "서버에 app.env를 만들고 DB 비밀번호와 API 키를 입력한 뒤, 저장소의 Compose 구성으로 Ledger를 실행하고 상태와 접속을 확인합니다."
series: "ledger"
chapter: "deploy"
seriesOrder: 3
---
## 이 글에서 할 일

서버에 **`/etc/ledger/app.env`를 만든 뒤** 완성된 Ledger를 Docker Compose로 실행합니다. 예시 파일은 저장소에 있지만 서버별 비밀번호와 키는 직접 입력해야 합니다. 앱 코드를 수정하는 단계가 아니라 실행 환경을 준비하는 단계입니다.

이 글은 호스트 PostgreSQL에 `ledger` DB와 `ledger_app` 계정이 준비되어 있고, `db/000`부터 `009`까지 적용한 Linux 서버를 기준으로 합니다. 저장소는 `/srv/devgear/ledger`에 있으며 Docker Compose를 사용할 수 있어야 합니다.

## 1단계. 운영 환경 파일 만들기

처음 설치하는 서버의 `/srv/devgear/ledger`에서 실행합니다.

```bash
cd /srv/devgear/ledger
sudo install -d -m 700 -o root -g root /etc/ledger
sudo install -m 600 -o root -g root deploy/app.env.example /etc/ledger/app.env
sudoedit /etc/ledger/app.env
```

`700`은 디렉터리를 root만 사용할 수 있게 하고, `600`은 파일을 root만 읽고 쓸 수 있게 합니다. **기존 `app.env`가 있다면 복사 명령을 다시 실행하지 않습니다.** 기존 값을 덮어쓰므로 `sudoedit`로 필요한 항목만 수정합니다.

`deploy/app.env.example` (저장소의 원본 예시)

```text
LEDGER_DB_URL=jdbc:postgresql://127.0.0.1:5432/ledger
LEDGER_DB_USER=ledger_app
LEDGER_DB_PASSWORD=
LEDGER_SIGNUP_CODE=
LEDGER_HOLIDAY_API_KEY=
LEDGER_MARKET_API_KEY=
LEDGER_MARKET_REALTIME=false
```

| 변수 | 입력할 값 |
|---|---|
| `LEDGER_DB_URL` | 호스트 DB 주소. 이 구성에서는 위 주소 사용 |
| `LEDGER_DB_USER` | DB에 만든 앱 계정 |
| `LEDGER_DB_PASSWORD` | 해당 DB 계정의 실제 비밀번호 |
| `LEDGER_SIGNUP_CODE` | 가입에 사용할 비공개 초대 코드 |
| `LEDGER_HOLIDAY_API_KEY` | 공휴일 API를 사용할 때 발급받은 키 |
| `LEDGER_MARKET_API_KEY` | 달러 결제·미국 주식 평가에 사용할 키 |
| `LEDGER_MARKET_REALTIME` | 실시간 시세 이용권에 맞춰 설정. 기본값 `false` |

DB 비밀번호는 반드시 DB 계정과 맞춥니다. 운영 파일의 값은 코드나 문서에 복사하지 않습니다. API 키 준비와 기능은 [공휴일 API](/posts/ledger-external-api-holiday-api), [환율·주식 API](/posts/ledger-external-api-market-api) 글에서 설명합니다.

가입 코드가 비어 있으면 신규 가입을 막습니다. 기존 사용자의 로그인과는 별개 설정입니다. API 키를 비워 두면 그 키가 필요한 외부 조회 기능을 사용하지 않으며, 기본 가계부 기능은 자체 DB로 동작합니다.

## 2단계. Compose가 파일을 읽는 방식 확인

`docker-compose.yml` (서비스 구성)

```yaml
name: ledger

services:
  app:
    build:
      context: .
    image: ledger:local
    container_name: ledger
    network_mode: host
    env_file:
      - /etc/ledger/app.env
    environment:
      CATALINA_OPTS: -Dtomcat.address=127.0.0.1
    mem_limit: 768m
    restart: unless-stopped
```

Compose가 `env_file`을 읽고 값을 컨테이너의 환경 변수로 넣습니다. Spring은 그 환경 변수를 `globals.properties`의 `${환경변수:기본값}`에서 읽습니다. 서버에 파일을 만든 다음 앱을 실행해야 이 연결이 완성됩니다.

`network_mode: host`는 컨테이너가 호스트의 네트워크를 함께 사용하는 설정입니다. 따라서 앱의 `127.0.0.1:5432`는 이 구성에서 호스트 PostgreSQL에 연결됩니다. `CATALINA_OPTS`는 Tomcat을 서버 내부 주소인 `127.0.0.1:8081`에 바인딩합니다.

`env_file`과 host 네트워크의 동작은 [Docker 환경 변수 문서](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/), [네트워크 문서](https://docs.docker.com/compose/how-tos/networking/)에서 확인했습니다(2026-10-07). 이 서버 구성을 Windows나 macOS의 로컬 실행에 그대로 적용한다고 가정하지 않습니다. 로컬 실행은 [IntelliJ 글](/posts/ledger-intro-local-run)을 따릅니다.

## 3단계. 구성 검사와 실행

같은 저장소 루트에서 실행합니다. root 소유 환경 파일을 읽으므로 `sudo`로 실행합니다.

```bash
sudo docker compose config --quiet
sudo docker compose up -d --build
```

첫 명령은 설정이 유효한지 검사하며 정상일 때 내용을 출력하지 않습니다. 오류가 나면 실행하지 않고 파일 경로와 형식을 먼저 확인합니다. `--quiet` 없이 설정 전체를 출력하면 환경 변수 값도 노출될 수 있으므로 공유용 로그에 남기지 않습니다.

두 번째 명령은 이미지를 빌드하고 앱을 백그라운드에서 실행합니다. 첫 빌드는 의존성과 이미지를 받아야 하므로 완료까지 기다립니다.

## 4단계. 상태와 접속 확인

서버의 저장소 루트에서 실행합니다.

```bash
sudo docker compose ps
sudo docker compose logs --tail=100 app
curl -I http://127.0.0.1:8081/p3/manifest.json
```

컨테이너가 실행 중이고, 상태 확인이 완료되면 `healthy`인지 봅니다. 정적 파일 요청은 HTTP 200을 기대합니다. 기동 직후에는 상태 확인 준비 시간 때문에 `starting`일 수 있습니다.

다음 [nginx 연결 글](/posts/ledger-deploy-nginx)을 적용한 뒤 외부 HTTPS 주소로 로그인하고 대시보드와 거래를 조회합니다. 이 단계에서 실제 DB 연결을 포함한 동작을 확인합니다.

## 환경 변수를 바꿨을 때

`app.env`를 수정한 뒤에는 같은 위치에서 컨테이너를 다시 만듭니다.

```bash
sudo docker compose up -d --force-recreate
```

새 컨테이너가 수정한 환경 변수를 받습니다. 실행 중인 컨테이너의 환경 변수는 파일을 저장하는 것만으로 바뀌지 않습니다. 실행 옵션은 [Docker Compose up 문서](https://docs.docker.com/reference/cli/docker/compose/up/), 설정 검사는 [config 문서](https://docs.docker.com/reference/cli/docker/compose/config/)에서 확인했습니다(2026-10-07).

## 정리

- 처음 설치할 때 예시를 `/etc/ledger/app.env`로 복사하고 실제 값을 입력합니다.
- Compose가 환경 파일을 읽고, Spring이 컨테이너 환경 변수에서 값을 받습니다.
- 앱 상태, 내부 HTTP 응답, 외부 로그인과 DB 조회를 차례로 확인합니다.
