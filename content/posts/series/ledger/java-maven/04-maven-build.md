---
title: "Maven으로 빌드하고 테스트하기"
slug: "ledger-java-maven-maven-build"
category: "development"
publishedAt: "2026-10-08"
summary: "mvn package와 mvn test가 내부에서 거치는 단계(라이프사이클), target 폴더에 생기는 결과물, war 안의 구조를 확인합니다."
series: "ledger"
chapter: "java-maven"
seriesOrder: 4
---
## 이 글에서 할 일

IntelliJ의 실행 버튼 뒤에서도, 서버의 Docker 빌드에서도 결국 Maven 명령이 돌아갑니다. 이 글에서는 자주 쓰는 Maven 명령과 그 결과물을 확인합니다.

## 자주 쓰는 명령

`pom.xml`이 있는 폴더에서 실행합니다.

```bash
mvn test        # 컴파일 + 테스트
mvn package     # 컴파일 + 테스트 + war 만들기
mvn clean       # target 폴더 지우기
mvn clean package -DskipTests   # 처음부터 다시, 테스트는 건너뛰고 war 만들기
```

## 라이프사이클: 명령은 단계의 이름입니다

Maven 명령의 `test`, `package`는 **빌드 단계**(phase)의 이름입니다. 단계는 순서가 정해져 있고, 어떤 단계를 실행하면 **그 앞 단계가 모두 먼저** 실행됩니다.

```text
validate → compile → test-compile → test → package → install
                                     │        │
                                  mvn test  mvn package
```

| 단계 | 하는 일 | 담당 플러그인 |
|---|---|---|
| `compile` | `src/main/java` 컴파일 → `target/classes` | maven-compiler-plugin |
| `test-compile` | `src/test/java` 컴파일 | maven-compiler-plugin |
| `test` | JUnit 테스트 실행. 하나라도 실패하면 **여기서 멈춤** | maven-surefire-plugin |
| `package` | war로 묶기 → `target/ledger.war` | maven-war-plugin |

그래서 `mvn package`만 실행해도 테스트가 함께 돕니다. 테스트가 실패하면 war가 만들어지지 않습니다. `-DskipTests`는 test 단계를 건너뛰게 하는 옵션입니다.

## target 폴더

빌드 결과는 모두 `target/`에 생깁니다. 저장소에는 올리지 않습니다(`.gitignore`에 `target/`).

```text
target/
├── classes/              ← 컴파일된 .class + resources 폴더 내용 (매퍼 XML, properties)
├── test-classes/
├── surefire-reports/     ← 테스트 결과 보고서
├── ledger/               ← war를 풀어 놓은 모양 (IntelliJ의 war exploded가 이것)
└── ledger.war
```

## war 안의 구조

```bash
unzip -l target/ledger.war | head -30
```

```text
index.jsp
manifest.json
resources/css/...
resources/js/...
WEB-INF/web.xml
WEB-INF/config/dispatcher-servlet.xml
WEB-INF/jsp/ledger/...
WEB-INF/classes/ledger/...                     ← 컴파일된 Java
WEB-INF/classes/sqlmap/mappers/ledger/...      ← src/main/resources 내용
WEB-INF/lib/spring-webmvc-6.2.7.jar ...        ← 의존성 jar
```

`src/main/webapp`의 내용이 war의 맨 위에, Java 클래스와 `resources` 폴더 내용은 `WEB-INF/classes`에, 의존성은 `WEB-INF/lib`에 들어갑니다. 설정 파일에서 `classpath:/sqlmap/...`이라고 쓰면 이 `WEB-INF/classes` 아래를 찾는다는 뜻입니다.

`provided` scope인 서블릿 API는 `WEB-INF/lib`에 없습니다. Tomcat이 제공하기 때문입니다([pom.xml 읽기](/posts/ledger-java-maven-pom) 참고).

## 테스트 결과 읽기

```bash
mvn test
```

명령이 끝나면 각 테스트 클래스의 `Tests run`, `Failures`, `Errors`, `Skipped`와 마지막 빌드 결과를 확인합니다. 성공 기준은 실패와 오류가 0이고 `BUILD SUCCESS`가 표시되는 것입니다. 여기서 실행 결과를 미리 단정하지 않고, 자신의 환경에서 나온 보고서를 읽습니다.

| 표시 | 뜻 |
|---|---|
| `Failures` | 검증(assert)이 틀림. 코드의 결과가 기대와 다름 |
| `Errors` | 테스트 도중 예외 발생 |
| `BUILD FAILURE` | 하나라도 실패. 위로 올라가 어떤 테스트가 왜 실패했는지 봄 |

자세한 실패 내용은 `target/surefire-reports/`의 파일에도 남습니다. 특정 테스트만 돌리려면 다음처럼 씁니다.

```bash
mvn test -Dtest=PayCycleTest
```

기존 테스트 코드와 경계 사례는 [테스트 장](/posts/ledger-test-existing-tests)에서 설명합니다.

## 서버에서는

서버의 Docker 빌드는 컨테이너 안에서 `mvn -B clean package -DskipTests`를 실행합니다(`-B`는 진행 표시 없이 로그만 남기는 배치 모드). 그 결과인 `target/ledger.war`를 Tomcat 이미지에 넣습니다. 자세한 과정은 [Dockerfile 글](/posts/ledger-deploy-dockerfile)에서 다룹니다.

## 정리

- `mvn test`, `mvn package`는 라이프사이클 단계 이름이고, 앞 단계가 모두 먼저 실행됩니다.
- 테스트가 실패하면 `package`까지 가지 않습니다. `-DskipTests`는 그 단계를 건너뜁니다.
- 결과물은 `target/ledger.war`이고, Java 클래스와 설정은 `WEB-INF/classes`, 라이브러리는 `WEB-INF/lib`에 들어갑니다.
