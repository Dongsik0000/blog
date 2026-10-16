---
title: "저장소의 JUnit 테스트와 실행"
slug: "fashion-archive-test-tests"
category: "development"
publishedAt: "2026-10-08"
summary: "Fashion Archive의 테스트 클래스 5개, 테스트 메서드 27개가 무엇을 확인하는지 정리하고, DB 없이 돌아가도록 검사 대상을 순수 함수로 나눈 방법, 실행 방법과 배포 스크립트에서 테스트가 함께 실행되는 구조를 설명합니다."
series: "fashion-archive"
chapter: "test"
seriesOrder: 1
---
## 이 글에서 할 일

Fashion Archive의 테스트는 `src/test/java` 아래 클래스 5개, `@Test` 메서드 27개입니다. 이 글에서는 무엇을 확인하는지, 어떻게 실행하는지 정리합니다.

## 무엇을 확인하나

| 테스트 클래스 | 확인하는 것 | 메서드 수 |
|---|---|---|
| `ValidationTest` | 글자 길이, URL(`http`/`https`만), 파일 형식(매직 넘버), 아이디 규칙 | 10 |
| `LoginAttemptLimiterTest` | 5회 허용과 6회째 차단, 5분 뒤 해제, 동시 요청, 키 상한 | 9 |
| `RequestUtilTest` | nginx 뒤에서 실제 IP를 믿는 조건 | 4 |
| `LocalStorageTest` | 삭제할 파일 경로가 업로드 폴더를 벗어나지 않는지 | 2 |
| `PhotoApiControllerTest` | 링크를 아이템 이름별로 묶고 순서를 지키는지 | 2 |

공통점은 **보안과 데이터에 직접 닿는 규칙**이라는 것입니다. 잘못된 주소가 저장되거나, 폴더 밖 파일이 지워지거나, 시도 제한이 뚫리면 화면만 봐서는 알아채기 어렵습니다.

## DB 없이 돌아가는 이유

테스트 대상은 모두 **DB와 Spring 없이** 입력만 받아 결과를 돌려주는 함수입니다.

| 대상 | 모양 |
|---|---|
| `Validation.requireHttpUrl(값)` | 문자열 → 문자열 또는 예외 |
| `Validation.imageExtension(바이트)` | 바이트 → `"jpg"` 등 또는 예외 |
| `LocalStorage.storedPath(폴더, 이름)` | 문자열 → 경로 또는 `null` |
| `PhotoApiController.groupLinks(목록)` | 목록 → 묶은 Map |
| `RequestUtil.clientIp(주소, 헤더)` | 문자열 → 문자열 |

`LocalStorage.storedPath`와 `RequestUtil.clientIp(String, String)`은 접근 제어자 없이(패키지 범위) `static`으로 두었습니다. 같은 패키지에 있는 테스트는 부를 수 있지만 다른 패키지의 코드에는 공개되지 않습니다. `RequestUtil`은 요청 객체를 받는 공개 메서드가 문자열 두 개를 받는 이 메서드를 부르는 구조라, 요청 객체를 만들지 않고도 판단 부분을 검사합니다.

```java
// 클라이언트 IP. 서버에서 Tomcat은 127.0.0.1에만 열려 있고 nginx가 X-Real-IP를 넣는다.
public static String clientIp(HttpServletRequest request) {
    return clientIp(request.getRemoteAddr(), request.getHeader("X-Real-IP"));
}

static String clientIp(String remoteAddr, String realIpHeader) { ... }
```

## 테스트 예: 아이디 규칙

`ValidationTest`

```java
// 폴더 이름으로 쓰이므로 경로 문자, 대문자, 공백, 한글과 길이 밖의 값은 막는다
@Test
void isLoginId_rejectsUnsafeOrOutOfRange() {
    assertFalse(Validation.isLoginId(null));
    assertFalse(Validation.isLoginId("ab"));
    assertFalse(Validation.isLoginId("a".repeat(31)));
    assertFalse(Validation.isLoginId(".."));
    assertFalse(Validation.isLoginId("../etc"));
    assertFalse(Validation.isLoginId("a/b"));
    assertFalse(Validation.isLoginId("a.b"));
    assertFalse(Validation.isLoginId("Admin"));
    assertFalse(Validation.isLoginId("ds kim"));
    assertFalse(Validation.isLoginId("사용자"));
}
```

막아야 하는 경우를 한 테스트에 나열합니다. 길이(2자, 31자), 경로 문자(`..`, `/`, `.`), 대문자, 공백, 한글입니다. 주석이 "왜 막는가"(폴더 이름으로 쓰임)를 함께 적습니다.

## 테스트 예: 삭제 경로

`LocalStorageTest`

```java
// 삭제 대상은 "{아이디}/{파일}" 형식만. 업로드 폴더를 벗어날 수 있는 이름은 null
@Test
void storedPath_rejectsUnsafeNames() {
    assertNull(LocalStorage.storedPath("/data/fashion-uploads", "a.jpg"));
    assertNull(LocalStorage.storedPath("/data/fashion-uploads", "../a.jpg"));
    assertNull(LocalStorage.storedPath("/data/fashion-uploads", "ds_kim1/../a.jpg"));
    assertNull(LocalStorage.storedPath("/data/fashion-uploads", "ds_kim1/x/a.jpg"));
    assertNull(LocalStorage.storedPath("/data/fashion-uploads", "ds_kim1/"));
    assertNull(LocalStorage.storedPath("/data/fashion-uploads", "Admin/a.jpg"));
}
```

| 이름 | 거절 이유 |
|---|---|
| `a.jpg` | 폴더가 없음 |
| `../a.jpg` | 폴더 부분이 아이디 규칙에 맞지 않음 |
| `ds_kim1/../a.jpg` | 파일 부분에 `/`와 `..` |
| `ds_kim1/x/a.jpg` | 하위 폴더 |
| `ds_kim1/` | 파일 이름이 비어 있음 |
| `Admin/a.jpg` | 대문자 폴더 |

파일을 실제로 지우는 `delete`가 아니라 **경로를 정하는 함수**만 검사하므로 디스크를 건드리지 않습니다([디스크 저장](/posts/fashion-archive-upload-local-storage)).

## 테스트 예: 링크 묶기

`PhotoApiControllerTest`

```java
// DB 결과 한 행을 흉내 낸다. 키는 컬럼명 그대로
private static Map<String, Object> link(long id, String label) { ... }

@Test
void groupLinks_groupsByLabelKeepingFirstSeenOrder() {
    List<Map<String, Object>> links = Arrays.asList(
            link(1, "아우터"), link(2, "바지"), link(3, "아우터"), link(4, "신발"), link(5, "바지"));

    Map<String, List<Map<String, Object>>> groups = PhotoApiController.groupLinks(links);

    assertEquals(Arrays.asList("아우터", "바지", "신발"), new ArrayList<String>(groups.keySet()));
    assertEquals(1L, groups.get("아우터").get(0).get("id"));
    assertEquals(3L, groups.get("아우터").get(1).get("id"));
    assertEquals(2, groups.get("바지").size());
    assertEquals(1, groups.get("신발").size());
}
```

DB 결과 모양(`item_label` 키)을 흉내 낸 Map으로, 아이템 이름이 **처음 나온 순서**(아우터 → 바지 → 신발)대로 묶이고 묶음 안의 순서도 지켜지는지 확인합니다([링크 묶기](/posts/fashion-archive-view-link-groups)).

## 실행

```bash
mvn test                          # 전체
mvn test -Dtest=ValidationTest    # 클래스 하나
```

`pom.xml`에 JUnit Jupiter 5.10.2와 maven-surefire-plugin 3.2.5가 지정되어 있습니다. 결과는 `target/surefire-reports/`에 남습니다.

## 배포할 때도 실행

`deploy/deploy.sh`

```bash
set -euo pipefail
...
# Dockerfile 이 target/fashion-archive.war 를 복사하므로 이미지 빌드 전에 war 를 만든다
mvn -q package
docker compose up -d --build
```

Maven의 `package` 단계는 `test` 단계를 포함합니다. 그래서 배포 스크립트의 `mvn -q package`가 **테스트를 함께 실행**하고, 하나라도 실패하면 `set -e` 때문에 스크립트가 거기서 멈춰 새 이미지를 만들지 않습니다. 실행 중인 앱은 이전 버전 그대로 남습니다([배포 스크립트](/posts/fashion-archive-deploy-deploy-actions)).

`PasswordHashTool`은 `src/test/java`에 있지만 `@Test`가 없는 `main` 프로그램이라 테스트로 실행되지 않습니다([BCrypt](/posts/fashion-archive-auth-bcrypt)).

## 정리

- 테스트 5개 클래스, 27개 메서드가 입력 검증, 파일 형식, 아이디 규칙, 시도 제한, 실제 IP, 삭제 경로, 링크 묶기를 확인합니다.
- 검사 대상을 DB와 Spring 없이 돌아가는 함수로 나눠 빠르게 실행합니다.
- 배포 스크립트의 `mvn -q package`가 테스트를 함께 실행해, 실패하면 배포가 멈춥니다.
