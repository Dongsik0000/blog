---
title: "개발 환경 준비와 로컬 실행"
slug: "fashion-archive-intro-local-run"
category: "development"
publishedAt: "2026-10-08"
summary: "JDK 17, Maven, PostgreSQL, Tomcat 9를 준비하고 schema.sql로 데이터베이스를 만든 뒤, 환경 변수와 /p1 컨텍스트로 Fashion Archive를 실행하는 과정을 설정 파일 기준으로 따라갑니다."
series: "fashion-archive"
chapter: "intro"
seriesOrder: 3
---
## 이 글에서 할 일

내 PC에서 Fashion Archive를 실행하는 데 필요한 것을 저장소의 설정 파일 기준으로 정리합니다. 순서는 **준비물 → 데이터베이스 → 환경 변수 → Tomcat 9에서 실행**입니다.

## 준비물

| 도구 | 버전 | 이유 |
|---|---|---|
| JDK | 17 | `pom.xml`의 `maven.compiler.release` 17 |
| Maven | | 의존성과 war 빌드. eGovFrame Maven 저장소에 접속할 수 있어야 함 |
| PostgreSQL | 16 | 운영과 같은 버전 |
| Tomcat | **9** | `javax.servlet` 기반이라 Tomcat 10 이상에서는 실행되지 않음 |

## 1단계. 데이터베이스 만들기

테이블 정의는 `src/main/resources/schema.sql` 한 파일에 있습니다. 카테고리 6개를 넣는 `INSERT`도 함께 있습니다.

```bash
createdb -U postgres fashion
psql -U postgres -d fashion -f src/main/resources/schema.sql
```

```sql
INSERT INTO category (slug, name, sort_order) VALUES
  ('spring','봄',1), ('summer','여름',2), ('autumn','가을',3), ('winter','겨울',4),
  ('accessory','악세사리',5), ('shoes','신발',6);
```

## 2단계. 환경 변수

설정은 `src/main/resources/egovProps/globals.properties`에 있고, 값은 환경 변수에서 옵니다.

```properties
# 값은 환경변수로 치환된다. ${NAME:default} 형식은 미설정 시 default 사용. 비밀값에는 기본값을 두지 않는다.
Globals.Fashion.Postgre.Url=${FASHION_DB_URL:jdbc:postgresql://localhost:5432/fashion}
Globals.Fashion.Postgre.UserName=${FASHION_DB_USER:postgres}
Globals.Fashion.Postgre.Password=${FASHION_DB_PASSWORD}

Globals.Fashion.Upload.Dir=${FASHION_UPLOAD_DIR:/data/fashion-uploads}
Globals.Fashion.Upload.BaseUrl=${FASHION_UPLOAD_BASE_URL:}

Globals.Fashion.SignupCode=${FASHION_SIGNUP_CODE:}
```

| 환경 변수 | 용도 | 기본값 |
|---|---|---|
| `FASHION_DB_URL`, `FASHION_DB_USER` | DB 접속 | `localhost:5432/fashion`, `postgres` |
| `FASHION_DB_PASSWORD` | DB 비밀번호 | **없음.** 반드시 넣어야 함 |
| `FASHION_UPLOAD_DIR` | 사진을 저장할 폴더 | `/data/fashion-uploads` |
| `FASHION_UPLOAD_BASE_URL` | 저장한 사진의 공개 주소 앞부분 | 비어 있음. 비면 사진 등록 시 "저장소 미설정" 오류 |
| `FASHION_SIGNUP_CODE` | 회원가입 코드 | 비어 있음. 비면 아무도 가입할 수 없음 |

비밀번호에는 기본값이 없습니다. 주석의 원칙대로 비밀값은 설정 파일에 기본값으로 남기지 않습니다. 값이 없으면 앱이 DB에 연결하지 못하므로 바로 알 수 있습니다.

### 사진 저장 폴더와 공개 주소

사진 기능까지 쓰려면 두 값을 짝으로 정합니다.

```text
FASHION_UPLOAD_DIR=저장할 폴더          (예: 사용자 홈 아래의 fashion-uploads)
FASHION_UPLOAD_BASE_URL=그 폴더를 보여 주는 주소   (예: http://localhost:8081/uploads)
```

앱은 파일을 `FASHION_UPLOAD_DIR/{아이디}/{파일}`에 쓰고, DB에는 `FASHION_UPLOAD_BASE_URL/{아이디}/{파일}`을 저장합니다. 브라우저는 이 주소로 사진을 불러오므로, 그 주소에서 폴더를 정적 파일로 보내 주는 서버가 있어야 사진이 보입니다. 운영에서는 nginx가 이 일을 합니다([nginx 연결](/posts/fashion-archive-deploy-nginx)).

## 3단계. 빌드

```bash
mvn test
mvn -q package -DskipTests
```

`target/fashion-archive.war`가 만들어집니다. 처음 빌드할 때 eGovFrame 저장소(`https://maven.egovframe.go.kr/maven/`)에서 실행환경 모듈을 받습니다.

## 4단계. Tomcat 9에서 /p1로 실행

운영에서는 war를 `webapps/p1.war`로 넣어 컨텍스트 경로를 `/p1`로 만듭니다. 로컬에서도 같은 경로로 실행하면 화면의 주소가 운영과 같습니다. IntelliJ IDEA Ultimate의 Tomcat 실행 구성이라면 Deployment의 Application context를 `/p1`로 지정합니다.

브라우저에서 `http://localhost:{포트}/p1/`로 접속하면 로그인 화면으로 이동합니다. `FASHION_SIGNUP_CODE`에 정한 코드로 `/p1/signup`에서 가입하고 로그인합니다.

## Docker로 실행하려면

`Dockerfile`은 로컬 Docker에서도 쓸 수 있게 만들어져 있습니다.

```dockerfile
# 서버에서는 Tomcat을 loopback에만 열고, 로컬 Docker에서는 기본값(0.0.0.0)으로 포트 매핑할 수 있게 한다.
ENV CATALINA_OPTS="-Dtomcat.address=0.0.0.0"
```

`docker-compose.yml`은 서버용 설정(호스트 네트워크, `/etc/fashion-archive/app.env`)이라 그대로 쓰지 않고, 이미지만 빌드해 포트를 연결해 실행합니다. Dockerfile은 미리 빌드한 `target/fashion-archive.war`를 복사하므로 3단계를 먼저 합니다.

## 정리

- JDK 17, PostgreSQL 16, **Tomcat 9**를 준비합니다.
- `schema.sql`로 테이블과 카테고리를 만듭니다.
- DB 비밀번호는 기본값이 없어 반드시 환경 변수로 넣고, 사진 기능은 저장 폴더와 공개 주소를 짝으로 정합니다.
- 운영과 같은 `/p1` 컨텍스트로 실행합니다.
