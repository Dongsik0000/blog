---
title: "DBeaver로 SSH 터널을 통해 접속하기"
slug: "oracle-cloud-postgresql-dbeaver"
category: "development"
publishedAt: "2026-10-07"
summary: "데이터베이스 포트를 열지 않고도 내 PC의 DBeaver에서 서버의 PostgreSQL을 보는 SSH 터널의 원리와 설정 방법을 설명합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 7
---
## 이 글에서 할 일

터미널의 psql만으로는 데이터를 보기 불편합니다. 내 PC의 데이터베이스 도구 **DBeaver**로 서버의 데이터베이스를 보고 싶습니다. 그런데 PostgreSQL은 외부에 열려 있지 않습니다. 이때 쓰는 방법이 **SSH 터널**입니다.

## SSH 터널의 원리

SSH 접속은 이미 열려 있습니다(22번). SSH는 원격 터미널뿐 아니라, **그 연결 안에 다른 통신을 담아 나르는 터널** 역할도 할 수 있습니다.

```text
내 PC                                       서버
DBeaver ──▶ (SSH 연결, 22번, 암호화) ──▶ sshd ──▶ 127.0.0.1:5432 (PostgreSQL)
```

PostgreSQL 입장에서는 서버 자신(`127.0.0.1`)이 접속한 것으로 보입니다. 그래서 [pg_hba.conf](/posts/oracle-cloud-postgresql-authentication)의 `127.0.0.1` 줄에 해당하고, 비밀번호 인증으로 접속됩니다. 5432번 포트를 외부에 열 필요가 전혀 없습니다.

## DBeaver 설정

새 연결에서 **PostgreSQL**을 고른 뒤 두 탭을 채웁니다.

### Main 탭 (데이터베이스 정보)

| 항목 | 값 | 설명 |
|---|---|---|
| Host | `localhost` | **서버 기준**의 주소입니다. 터널을 지나면 서버 안에서 접속하는 셈이라 localhost입니다 |
| Port | `5432` | |
| Database | `myapp` | |
| Username | `myapp_user` | |
| Password | 계정을 만들 때 정한 비밀번호 | |

### SSH 탭 (터널 정보)

| 항목 | 값 |
|---|---|
| Use SSH Tunnel | 체크 |
| Host/IP | 서버 공인 IP (또는 도메인) |
| Port | `22` |
| User | `ubuntu` |
| Authentication | Public Key |
| Private key | 서버 접속에 쓰는 개인 키 파일 |

<!-- 📷 스크린샷: DBeaver Main 탭과 SSH 탭 설정 화면 -->

**Test Connection**을 눌러 성공하면 저장합니다.

| 결과 | 의미 |
|---|---|
| 성공 | 터널과 DB 인증 모두 정상 |
| SSH 관련 오류 (`Auth fail` 등) | SSH 탭의 사용자, 키 파일을 확인합니다. 터미널에서 `ssh`로 접속되는지 먼저 봅니다 |
| `password authentication failed` | 터널은 됐고 DB 비밀번호가 틀렸습니다 |

## 명령으로 터널 만들기 (DBeaver 없이)

DBeaver가 내부에서 하는 일을 명령으로 직접 할 수도 있습니다. 내 PC에서 실행합니다.

```bash
ssh -N -L 15432:127.0.0.1:5432 ubuntu@<공인 IP>
```

| 옵션 | 뜻 |
|---|---|
| `-L 15432:127.0.0.1:5432` | 내 PC의 15432번 포트로 들어온 연결을 서버에서 `127.0.0.1:5432`로 보냄 |
| `-N` | 원격 명령을 실행하지 않고 터널만 유지 |

이 창을 켜 둔 채로 다른 창에서 내 PC의 15432번으로 접속하면 서버의 PostgreSQL에 연결됩니다.

```bash
psql -h 127.0.0.1 -p 15432 -U myapp_user -d myapp
```

15432처럼 내 PC에서 쓰지 않는 포트를 고르는 이유는, 내 PC에도 PostgreSQL이 설치되어 5432를 쓰고 있을 수 있기 때문입니다.

## 조심할 것

- 이 방법은 **운영 데이터베이스에 직접 접속**하는 것입니다. 데이터를 바꿀 때는 [트랜잭션 습관](/posts/oracle-cloud-postgresql-psql-basics)을 지킵니다.
- 앱 계정으로 접속하면 앱 권한만큼만 할 수 있어 실수의 범위가 줄어듭니다. 슈퍼유저 계정은 꼭 필요할 때만 씁니다.

## 정리

- SSH 터널은 이미 열려 있는 SSH(22) 연결 안에 데이터베이스 통신을 담아 나릅니다.
- DBeaver의 Main 탭 Host는 **서버 기준**이라 `localhost`, SSH 탭에는 서버 접속 정보를 넣습니다.
- 데이터베이스 포트(5432)를 외부에 열 필요가 없습니다.
