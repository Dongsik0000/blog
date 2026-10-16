---
title: "개발 환경 준비와 로컬 실행"
slug: "ledger-intro-local-run"
category: "development"
publishedAt: "2026-10-08"
summary: "JDK 17, Maven, PostgreSQL, Tomcat 10.1을 준비하고, SQL 파일로 데이터베이스를 만든 뒤 IntelliJ에서 Ledger를 /p3 경로로 띄우는 과정을 따라갑니다."
series: "ledger"
chapter: "intro"
seriesOrder: 3
---
## 이 글에서 할 일

완성된 Ledger 저장소를 내 PC에서 실행합니다. 순서는 **저장소 준비 → 데이터베이스 만들기 → 환경 변수 설정 → IntelliJ에서 Tomcat으로 실행**입니다. 앱의 Java와 JSP 코드는 저장소에 들어 있으므로, 실행에 필요한 환경을 준비합니다.

## 준비물

| 도구 | 버전 | 용도 |
|---|---|---|
| JDK | 17 | Java 컴파일과 실행 |
| Maven | 3.9 | 의존성 내려받기와 빌드 (IntelliJ에 내장된 것도 가능) |
| PostgreSQL | 16 | 데이터베이스 |
| Tomcat | 10.1 | war 실행. 공식 사이트에서 zip을 받아 압축만 풀면 됩니다 |
| IntelliJ IDEA | Tomcat 연동 기능을 사용할 수 있는 환경 | Tomcat 실행 구성 |

Tomcat은 **10.1**이어야 합니다. 9 이하는 `javax.servlet`을 쓰는데, 이 프로젝트는 `jakarta.servlet`을 쓰기 때문입니다([기술 구성](/posts/ledger-intro-tech-stack) 참고).

## 1단계. 저장소 준비

작업 폴더에서 저장소를 내려받습니다. 이미 내려받았다면 기존 `ledger` 폴더를 사용합니다.

```bash
git clone https://github.com/Dongsik0000/ledger.git
cd ledger
```

이후 터미널 명령은 `pom.xml`과 `db/`가 있는 **Ledger 저장소 루트**에서 실행합니다. IntelliJ에서도 이 폴더를 Maven 프로젝트로 엽니다.

## 2단계. 데이터베이스 만들기

새 개발 DB에 `db/` 폴더의 SQL 파일을 **번호 순서대로** 실행합니다. 번호는 스키마가 바뀐 순서이기 때문입니다([스키마 변경 파일](/posts/ledger-database-migrations) 참고). 이미 실행한 DB에는 처음부터 다시 적용하지 않습니다.

`db/000_create_db.sql`에는 개발용 계정 비밀번호가 `ledger_app`으로 적혀 있습니다. 로컬 기본값으로 실행할 때는 그대로 사용하고, 다른 비밀번호를 사용한다면 DB 계정과 아래 `LEDGER_DB_PASSWORD`를 같은 값으로 맞춥니다. 이 개발용 비밀번호를 운영에서 사용하지 않습니다.

```bash
psql -h 127.0.0.1 -U postgres -v ON_ERROR_STOP=1 -f db/000_create_db.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/001_schema.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/002_seed_holiday_2026.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/003_comments.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/004_recurring_installment.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/005_opening_balance.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/006_market_valuation.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/007_asset_transfers.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/008_bank_account.sql
psql -h 127.0.0.1 -U ledger_app -d ledger -v ON_ERROR_STOP=1 -f db/009_default_account.sql
```

첫 명령은 PostgreSQL 관리자 계정으로 앱 계정과 DB를 만듭니다. 나머지는 `ledger_app` 계정으로 테이블과 컬럼을 추가합니다. `ON_ERROR_STOP=1`은 SQL 오류가 나면 해당 파일의 실행을 중단하는 옵션이며, 오류가 나면 다음 파일로 넘어가기 전에 원인을 확인합니다.

접속 옵션과 `ON_ERROR_STOP`의 동작은 [PostgreSQL 16의 psql 문서](https://www.postgresql.org/docs/16/app-psql.html)에서 확인했습니다(2026-10-07).

## 3단계. 환경 변수 설정

설정은 `src/main/resources/egovProps/globals.properties`에 있고, 값은 **환경 변수가 있으면 환경 변수, 없으면 콜론 뒤 기본값**을 씁니다.

`src/main/resources/egovProps/globals.properties` (DB와 가입 코드 부분)

```properties
Globals.Ledger.Postgre.Url=${LEDGER_DB_URL:jdbc:postgresql://localhost:5432/ledger}
Globals.Ledger.Postgre.UserName=${LEDGER_DB_USER:ledger_app}
Globals.Ledger.Postgre.Password=${LEDGER_DB_PASSWORD:ledger_app}
Globals.Ledger.SignupCode=${LEDGER_SIGNUP_CODE:dev-signup}
```

| 환경 변수 | 용도 | 로컬 기본값 |
|---|---|---|
| `LEDGER_DB_URL`, `LEDGER_DB_USER`, `LEDGER_DB_PASSWORD` | DB 연결 | `localhost:5432/ledger`, `ledger_app` |
| `LEDGER_SIGNUP_CODE` | 회원가입 초대 코드 | `dev-signup` |
| `LEDGER_HOLIDAY_API_KEY` | 공휴일 API 키 | 없음 (공휴일 직접 추가만 가능) |
| `LEDGER_MARKET_API_KEY` | 환율, 주식 시세 API 키 | 없음 (달러 결제, 주식 평가 사용 불가) |
| `LEDGER_MARKET_REALTIME` | 실시간 시세 이용권 사용 여부 | `false` |
| `LEDGER_LOG_LEVEL` | 로그 수준 (`logback.xml`) | `DEBUG` |

DB 비밀번호가 개발 기본값과 같다면 별도 환경 변수 없이 기본 가계부 기능을 실행할 수 있습니다. 비밀번호를 바꾸거나 API 기능을 사용한다면 **Tomcat 실행 구성의 Environment variables**에 해당 값을 넣습니다. 파일을 프로젝트 폴더에 두는 것만으로 IntelliJ나 Spring이 자동으로 읽지는 않습니다.

서버의 Docker 실행은 `/etc/ledger/app.env` 파일을 별도로 만들어야 합니다. 로컬 IntelliJ 실행과 주입 방식이 다르며, [서버 실행 글](/posts/ledger-deploy-compose-env)에서 생성과 입력 순서를 다룹니다.

## 4단계. IntelliJ에 Tomcat 등록 (PC당 한 번)

**File → Settings → Build, Execution, Deployment → Application Servers**에서 `+` → **Tomcat Server**를 고르고, Tomcat Home에 압축을 푼 Tomcat 10.1 폴더를 지정합니다.

## 5단계. 실행 구성

**Run → Edit Configurations** → `+` → **Tomcat Server → Local**

| 탭 | 항목 | 값 | 이유 |
|---|---|---|---|
| Server | URL (Open browser) | `http://localhost:8081/p3/` | 실행 후 브라우저로 열 주소 |
| Server | On 'Update' action | `Update classes and resources` | Update 작업으로 클래스와 리소스 갱신 |
| Server | HTTP port | `8081` | 실제 Tomcat 포트. URL과 별개 항목입니다 |
| Deployment | Artifact | `ledger:war exploded` | 압축하지 않은 war. 수정이 빨리 반영됩니다 |
| Deployment | Application context | `/p3` | 서버와 같은 경로. **끝에 슬래시를 붙이지 않습니다** |

<!-- 📷 스크린샷: IntelliJ Tomcat 실행 구성의 Server 탭과 Deployment 탭 -->

`ledger:war exploded`가 목록에 없으면 Maven 창에서 **Reload All Maven Projects**를 누른 뒤 다시 엽니다.

### 왜 `/p3`와 8081인가

운영 서버에서 Ledger는 `https://도메인/p3/`에서, Tomcat 8081 포트로 실행됩니다. 로컬도 같은 경로와 포트로 맞춰 두면 "로컬에서는 되는데 서버에서는 링크가 깨지는" 문제가 생기지 않습니다. JSP와 JS는 경로를 직접 쓰지 않고 `contextPath`를 앞에 붙이므로, 경로가 같으면 동작도 같습니다.

IntelliJ 메뉴와 기능은 설치 버전에 따라 다를 수 있습니다. 위 항목의 의미는 [JetBrains의 Tomcat 실행 구성 문서](https://www.jetbrains.com/help/idea/run-debug-configuration-tomcat-server.html)에서 확인했습니다(2026-10-07). `On 'Update' action`은 파일 저장 자체가 아니라 Update 작업 때의 동작을 정합니다.

## 6단계. 실행하고 확인

실행 버튼을 누르면 브라우저가 `http://localhost:8081/p3/`를 엽니다. 로그인하지 않았으므로 로그인 화면으로 이동합니다. **회원가입**에서 초대 코드 `dev-signup`으로 가입하고 로그인하면 대시보드가 보입니다.

## 자주 나는 오류

| 메시지 | 원인과 해결 |
|---|---|
| `Error during artifact deployment` | 배포 로그에서 원인을 확인합니다. Artifact와 Application context가 각각 준비한 WAR와 `/p3`인지 확인합니다 |
| `Address localhost:1099 is already in use` | 이전에 띄운 Tomcat이 아직 살아 있습니다. 그 프로세스를 종료합니다 |
| `Cannot open URL` | URL만 8081로 바꾸고 HTTP port는 그대로 두었습니다 |
| DB 연결 오류 (`Connection refused`, `password authentication failed`) | PostgreSQL이 꺼져 있거나, 비밀번호가 환경 변수 기본값과 다릅니다 |

## 정리

- JDK 17, PostgreSQL 16, Tomcat 10.1과 Tomcat 연동이 가능한 IntelliJ를 준비합니다.
- `db/`의 SQL을 번호 순서대로 실행합니다.
- DB 계정과 연결 비밀번호를 맞추고, API 키는 실행 구성의 환경 변수로 넣습니다.
- Docker로 서버에 띄울 때는 별도로 `/etc/ledger/app.env`를 만듭니다.
- IntelliJ에서 context `/p3`, 포트 8081로 띄워 서버와 경로를 맞춥니다.
