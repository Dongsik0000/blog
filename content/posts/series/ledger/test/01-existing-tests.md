---
title: "저장소에 있는 JUnit 테스트 실행하기"
slug: "ledger-test-existing-tests"
category: "development"
publishedAt: "2026-10-08"
summary: "Ledger의 기존 테스트가 확인하는 기능을 정리하고, Maven으로 전체 또는 일부 테스트를 실행해 결과 보고서를 읽는 방법을 설명합니다."
series: "ledger"
chapter: "test"
seriesOrder: 1
---
## 이 글에서 할 일

완성된 Ledger에는 날짜, 금액, 입력 해석, 응답 해석을 확인하는 JUnit 테스트가 있습니다. 새 테스트를 만들기 전에 저장소의 `src/test/java`를 읽고 기존 테스트를 실행합니다.

**단위 테스트**는 작은 계산이나 규칙에 입력을 넣고 기대한 결과가 나오는지 확인하는 코드입니다. 테스트 파일이 있다는 사실과 현재 환경에서 실행에 성공했다는 사실은 구분하며, 실행 결과는 Maven 보고서로 확인합니다.

## 어떤 규칙을 확인하는가

| 테스트 클래스 | 확인하는 내용 |
|---|---|
| `PayCycleTest` | 짧은 달, 윤년, 주말·휴일, 주기 경계 |
| `OpeningBalanceTest` | 시작 잔액과 기준일 이후 거래 계산 |
| `InstallmentTest` | 회차 금액, 나머지 금액, 연도를 넘는 할부 |
| `RecurringGeneratorTest` | 지난·미래 결제일, 휴일 보정, 마지막 회차 |
| `TransferAdjustmentTest` | 거래 금액·이체 대상 변경 시 자산 증감 |
| `SummaryTablesTest` | 월 목록, 카테고리 그룹과 소계, 결제수단 표 |
| `LoginAttemptLimiterTest` | 횟수 제한, 차단 만료, 동시 요청, 기록 상한 |
| `ParamUtilTest`, `LikeUtilTest`, `CsvUtilTest` | 입력 변환, 검색 문자, CSV 형식과 수식 방어 |
| `RequestUtilTest` | 프록시 주소를 신뢰하는 조건 |
| `HolidayXmlParserTest` | 공휴일 XML의 정상·오류 응답과 DOCTYPE 차단 |
| `MarketValueTest`, `MarketDataClientTest` | 원화 반올림과 미국 종목 검색 결과 해석 |
| Controller의 테스트 클래스 | 로그인 제한 키, 순서 입력, 고정 항목 일정 규칙 |

이 목록은 저장소의 실제 테스트 파일을 기준으로 작성했습니다. DB 연결이나 화면 클릭 전체를 각 테스트가 실행하는지는 호출 코드를 따로 확인합니다.

## 1단계. 전체 실행

개발 PC의 Ledger 저장소 루트에서 실행합니다. JDK 17과 Maven이 준비되어 있어야 합니다.

```bash
mvn test
```

Maven은 앱과 테스트를 컴파일한 뒤 JUnit 테스트를 실행합니다. 처음 실행할 때는 의존성을 내려받으며, `target/`에 컴파일 결과와 보고서가 생깁니다.

마지막에 `BUILD SUCCESS`가 나오고 각 보고서의 실패와 오류가 0인지 확인합니다. 컴파일이나 의존성 다운로드가 실패한 경우에는 테스트 검증 실패와 구분해 앞선 오류를 먼저 확인합니다.

## 2단계. 특정 테스트 실행

같은 저장소 루트에서 실행합니다.

```bash
mvn test -Dtest=PayCycleTest
mvn test -Dtest=InstallmentTest,TransferAdjustmentTest
```

차례로 클래스 하나와 두 클래스를 실행합니다. 지정 방식은 [Maven Surefire 공식 문서](https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html)에서 확인했습니다(2026-10-07). 프로젝트는 `pom.xml`에 Surefire 3.5.2를 지정하고 있습니다.

## 3단계. 실패 보고서 읽기

`target/surefire-reports/`의 보고서를 엽니다. 테스트 이름과 `expected`·`actual`을 비교해 어떤 입력에서 기대값과 계산 결과가 달랐는지 확인합니다.

| 결과 항목 | 뜻 |
|---|---|
| `Tests run` | 실행한 테스트 수 |
| `Failures` | 기대값을 확인하는 검증이 실패한 수 |
| `Errors` | 테스트 실행 중 예외 등으로 실패한 수 |
| `Skipped` | 실행하지 않은 테스트 수 |

테스트 장의 코드 예시는 현재 저장소에서 발췌한 것입니다. 이 글에서 실행 결과를 대신 제시하지 않으며, 자신의 환경에서 나온 보고서를 확인합니다.

## 배포와 연결하기

일반 `mvn package`는 테스트 뒤에 WAR를 만듭니다. 저장소의 Dockerfile은 `-DskipTests`를 사용하므로, 배포 전에 개발 환경에서 위 테스트 결과를 확인합니다. Maven의 단계와 결과물 구조는 [빌드 글](/posts/ledger-java-maven-maven-build)에서 다룹니다.
