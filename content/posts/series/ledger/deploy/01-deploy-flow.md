---
title: "완성된 Ledger를 서버에 올리는 순서"
slug: "ledger-deploy-deploy-flow"
category: "development"
publishedAt: "2026-10-08"
summary: "저장소에 들어 있는 배포 파일의 역할을 확인하고, DB와 app.env 준비부터 Docker 실행, nginx 연결, 자동 배포 설정까지 순서를 정리합니다."
series: "ledger"
chapter: "deploy"
seriesOrder: 1
---
## 이 글에서 할 일

완성된 Ledger를 서버에서 실행합니다. Java 코드와 배포 파일은 저장소에 들어 있습니다. 서버에는 DB, 운영 설정값, 웹 서버 연결을 준비합니다.

이 장은 저장소의 `Dockerfile`, `docker-compose.yml`, `deploy/`, `.github/workflows/deploy.yml`을 기준으로 설명합니다. 명령은 해당 서버에 직접 적용할 때 실행하는 절차입니다.

## 요청이 지나가는 길

```text
브라우저: https://도메인/p3/
    ↓
nginx: HTTPS 요청을 받아 /p3/ 경로를 전달
    ↓
Tomcat: 127.0.0.1:8081에서 p3.war 실행
    ↓
호스트 PostgreSQL: 127.0.0.1:5432의 ledger DB
```

Compose에 정의된 서비스는 앱 하나입니다. PostgreSQL과 nginx는 호스트 서버에서 따로 실행합니다. 따라서 `docker compose up`만으로 DB와 nginx까지 설치되는 구성은 아닙니다.

## 저장소 파일과 서버에서 준비할 것

| 저장소의 파일 | 역할 | 서버에서 준비할 것 |
|---|---|---|
| `db/000`부터 `009`까지 SQL | 계정, 테이블, 기능별 컬럼 | DB를 만들고 SQL 적용 |
| `deploy/app.env.example` | 환경 변수 이름과 기본 형식 | `/etc/ledger/app.env` 생성 및 실제 값 입력 |
| `Dockerfile` | WAR 빌드와 Tomcat 실행 이미지 | Docker 실행 환경 |
| `docker-compose.yml` | 네트워크, 환경 변수, 메모리 설정 | 앱 이미지 빌드와 컨테이너 실행 |
| `deploy/nginx-ledger.conf` | `/p3/` 프록시와 요청 제한 | nginx 설정에 포함 |
| `.github/workflows/deploy.yml` | SSH 자동 배포 | GitHub Secrets와 배포 키 |
| `deploy/ledger-backup.timer` | 매일 DB 백업 예약 | 백업 스크립트와 systemd 단위 설치 |

## 처음 올리는 순서

1. Linux 서버에 PostgreSQL, Docker Compose, nginx를 준비합니다. 공통 서버 준비는 [Oracle Cloud 시리즈](/series/oracle-cloud)에서 다룹니다.
2. Ledger 저장소를 `/srv/devgear/ledger`에 준비합니다. 배포 스크립트가 이 경로를 사용합니다.
3. 새 DB에 SQL을 `000`부터 `009`까지 적용합니다. 운영 비밀번호는 개발 기본값을 바꾸고, 앱의 DB 연결 값과 맞춥니다.
4. `/etc/ledger/app.env`를 만들고 실제 값을 입력합니다.
5. Docker 이미지를 빌드해 앱을 실행하고 서버 내부에서 응답을 확인합니다.
6. nginx에 `/p3/` 설정을 포함해 외부 HTTPS 주소로 접속합니다.
7. GitHub Actions의 배포 키와 Secrets를 등록합니다.
8. 매일 DB 백업을 실행할 systemd 타이머를 등록합니다.

DB 준비는 [SQL 적용 글](/posts/ledger-database-migrations), 4번과 5번은 [Compose와 app.env 글](/posts/ledger-deploy-compose-env)로 이어집니다. 처음 설치할 때 SQL이 하나라도 실패하면 원인을 해결한 뒤 다음 단계로 넘어갑니다.

## 실행 후 확인하는 기준

컨테이너가 실행 중인지, Tomcat이 응답하는지, 외부에서 로그인할 수 있는지를 따로 확인합니다. 정적 파일의 HTTP 200 응답은 Tomcat과 경로 확인이며, DB 연결과 로그인까지 확인하는 검사는 아닙니다.

운영 URL에서 로그인한 뒤 대시보드와 기존 거래를 조회하면 앱과 DB가 연결된 흐름을 확인할 수 있습니다. 새 설치에서는 초대 코드를 설정한 뒤 자신의 계정으로 가입하고 같은 흐름을 확인합니다.

## 정리

- 서버 준비, DB, 환경 변수, 앱 실행, nginx 연결 순서로 진행합니다.
- `/etc/ledger/app.env`는 예시를 바탕으로 서버에 직접 만듭니다.
- 앱 실행 확인과 실제 로그인·조회 확인을 나누어 수행합니다.
