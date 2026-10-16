---
title: "폴더와 패키지 구조"
slug: "ledger-intro-project-structure"
category: "development"
publishedAt: "2026-10-08"
summary: "Maven 표준 폴더 구조와, 기능마다 controller, service, impl, dao로 나눈 패키지 구조를 따라가며 파일 하나가 어디에 있어야 하는지 정리합니다."
series: "ledger"
chapter: "intro"
seriesOrder: 4
---
## 이 글에서 할 일

처음 프로젝트를 열면 폴더가 많아서 어디부터 봐야 할지 모릅니다. 이 글에서는 폴더의 규칙을 이해해서, 기능 하나를 고칠 때 **어떤 파일들을 열어야 하는지** 바로 찾을 수 있게 합니다.

## 저장소 맨 위

```text
ledger/
├── pom.xml                  ← Maven 설정: 의존성, 빌드 방법
├── src/                     ← 소스 코드
├── db/                      ← 데이터베이스 SQL (000 ~ 009, 번호 순서대로 실행)
├── deploy/                  ← 서버 배포용 파일 (배포 스크립트, nginx, 백업, 환경 변수 예시)
├── Dockerfile               ← 이미지 만드는 방법
├── docker-compose.yml       ← 서버에서 컨테이너 실행 방법
└── .github/workflows/       ← GitHub Actions 자동 배포
```

## src: Maven 표준 폴더

Maven은 폴더 이름을 약속으로 정해 둡니다. 이 약속을 따르면 설정 없이도 Maven이 알아서 찾습니다.

```text
src/
├── main/
│   ├── java/        ← Java 소스 (.java)
│   ├── resources/   ← 설정 파일, SQL 매퍼 XML (빌드하면 classpath로 들어감)
│   └── webapp/      ← 웹 파일: JSP, CSS, JS, 이미지, web.xml
└── test/
    └── java/        ← 테스트 코드 (war에는 들어가지 않음)
```

## src/main/java: 기능별 패키지

```text
ledger/
├── auth/         로그인, 가입
├── dashboard/    대시보드
├── entry/        거래 내역
├── recurring/    고정 항목, 자동 기록 스케줄러, 할부
├── account/      통장, 이체
├── asset/        자산
├── summary/      요약 통계
├── settings/     설정
├── holiday/      공휴일 (외부 API 포함)
├── market/       환율, 주식 시세 (외부 API)
├── cycle/        주기 계산, 시작 잔액 (순수 계산 클래스)
├── interceptor/  로그인 확인, POST 요청 검사
└── cmmn/         공통: 응답 형식, 결과 코드, 유틸리티, 예외 처리
```

**기능(도메인)별로 먼저 나누고, 그 안을 계층별로 나눕니다.** `settings` 패키지를 예로 보면 다음과 같습니다.

```text
settings/
├── controller/SettingsApiController.java    ← 요청을 받고, 입력 검사와 로직
├── service/SettingsService.java             ← 인터페이스: 어떤 기능이 있는지
├── service/impl/SettingsServiceImpl.java    ← 구현: DAO 호출
└── dao/SettingsDAO.java                     ← MyBatis로 SQL 실행
```

| 계층 | 이름 규칙 | 하는 일 |
|---|---|---|
| Controller | `XxxApiController` | URL과 연결. 입력을 검사하고 로직을 수행한 뒤 응답 |
| Service | `XxxService` (인터페이스) | 기능 목록 |
| ServiceImpl | `XxxServiceImpl` | 서비스 구현. 이 프로젝트에서는 DAO를 그대로 호출 |
| DAO | `XxxDAO` | 매퍼 XML의 SQL 실행 |

이 이름 규칙(`service/impl`, `DAO`, 공통 패키지 `cmmn`)은 전자정부 프레임워크 프로젝트의 관례를 따른 것입니다. 로직을 Controller에 둔 이유는 [Spring MVC 장](/posts/ledger-spring-logic-in-controller)에서 설명합니다.

### 계층 없이 클래스만 있는 패키지

`cycle/PayCycle.java`, `recurring/Installment.java`, `entry/TransferAdjustment.java` 같은 클래스는 DB나 Spring에 의존하지 않는 **순수 계산 클래스**입니다. 날짜와 금액을 받아 결과만 돌려주므로 테스트하기 쉽습니다([테스트 장](/posts/ledger-test-pure-functions)).

## src/main/resources: 설정과 SQL

```text
resources/
├── spring/context-datasource.xml     ← DB 연결, MyBatis, 트랜잭션, 비밀번호 해시 빈
├── sqlmap/mybatis-config.xml         ← MyBatis 공통 설정
├── sqlmap/mappers/ledger/<기능>/<기능>.xml   ← 기능별 SQL
├── egovProps/globals.properties      ← 설정값 (환경 변수 참조)
├── message/message-common.properties ← 메시지
└── logback.xml                        ← 로그 설정
```

SQL 매퍼도 Java 패키지와 같은 기능 이름으로 나눕니다. `SettingsDAO`의 SQL은 `mappers/ledger/settings/settings.xml`에 있습니다.

## src/main/webapp: 화면

```text
webapp/
├── WEB-INF/
│   ├── web.xml                     ← 웹 앱 시작 설정
│   ├── config/dispatcher-servlet.xml  ← Spring MVC 설정
│   ├── jsp/ledger/<기능>/<기능>Main.jsp ← 화면
│   ├── tags/layout.tag, xxxEditor.tag ← 공통 레이아웃, 편집 창 조각
│   └── layout/ledgerMenu.jsp, icons.jsp
├── resources/
│   ├── css/
│   ├── js/common/common.js, modal.js   ← 공통 JS
│   ├── js/app/<기능>/<기능>.js          ← 화면별 JS
│   └── js/lib/chartjs/
├── index.jsp                       ← /p3/ 로 들어오면 대시보드로 보냄
└── manifest.json                   ← 홈 화면 아이콘
```

`WEB-INF` 안의 파일은 **브라우저에서 직접 열 수 없습니다.** JSP를 여기 두면 반드시 컨트롤러를 거쳐야만 화면이 보이므로, 로그인 확인 같은 검사를 건너뛸 수 없습니다. 브라우저가 직접 받아야 하는 CSS, JS, 이미지는 `WEB-INF` 밖의 `resources/`에 둡니다.

## 기능 하나에 열어야 할 파일

예를 들어 "설정 화면에 항목을 하나 추가"하려면 다음 파일을 엽니다.

| 순서 | 파일 | 할 일 |
|---|---|---|
| 1 | `db/0XX_*.sql` | 컬럼이 필요하면 새 SQL 파일 |
| 2 | `mappers/ledger/settings/settings.xml` | SQL 추가 |
| 3 | `settings/dao/SettingsDAO.java` | SQL을 부르는 메서드 |
| 4 | `settings/service/SettingsService.java`, `impl/SettingsServiceImpl.java` | 서비스 메서드 |
| 5 | `settings/controller/SettingsApiController.java` | URL과 입력 검사 |
| 6 | `WEB-INF/jsp/ledger/settings/settingsMain.jsp` | 화면 요소 |
| 7 | `resources/js/app/settings/settings.js` | Ajax 호출과 결과 처리 |

기능 이름(`settings`)이 모든 층의 폴더와 파일 이름에 들어가 있어서, 하나를 찾으면 나머지도 같은 이름으로 찾을 수 있습니다.

## 정리

- 폴더는 Maven 표준(`src/main/java`, `resources`, `webapp`, `src/test/java`)을 따릅니다.
- Java는 **기능별 패키지 → 계층별 하위 패키지**로 나눕니다.
- SQL, JSP, JS도 같은 기능 이름으로 나눠 한 기능의 파일을 쉽게 찾습니다.
- JSP는 `WEB-INF` 안에 두어 컨트롤러를 거쳐야만 보이게 합니다.
