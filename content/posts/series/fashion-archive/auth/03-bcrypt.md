---
title: "BCrypt만 쓰기 위한 spring-security-core"
slug: "fashion-archive-auth-bcrypt"
category: "development"
publishedAt: "2026-10-08"
summary: "비밀번호를 원문 대신 BCrypt 해시로 저장하는 이유, Spring Security 전체가 아니라 spring-security-core의 BCryptPasswordEncoder만 빈으로 쓰는 구성, encode와 matches의 동작, 해시를 만드는 PasswordHashTool을 설명합니다."
series: "fashion-archive"
chapter: "auth"
seriesOrder: 3
---
## 이 글에서 할 일

DB의 `users.password_hash`에는 비밀번호 원문이 아니라 BCrypt **해시**가 들어 있습니다. 이 글에서는 왜 BCrypt인지, Fashion Archive가 Spring Security를 어떻게 일부만 쓰는지 봅니다.

## 비밀번호를 해시로 저장하는 이유

해시는 되돌릴 수 없는 변환입니다. DB가 유출되어도 원래 비밀번호를 바로 알 수 없습니다.

| 방식 | 문제 |
|---|---|
| 원문 저장 | 유출되면 그대로 노출 |
| SHA-256 같은 빠른 해시 | 너무 빨라 무작위 대입이 쉬움. 같은 비밀번호는 같은 해시라 미리 계산한 표로 찾을 수 있음 |
| **BCrypt** | 일부러 느림. 해시마다 무작위 **솔트**를 섞어 같은 비밀번호도 매번 다른 해시 |

설계 문서의 결정 표에서도 비밀번호 해시를 SHA-256이 아니라 BCrypt로 정했습니다.

BCrypt 해시는 다음과 같이 생겼습니다. 알고리즘 버전, 반복 강도, 솔트, 해시가 한 문자열에 들어 있어 따로 저장할 것이 없습니다.

```text
$2a$10$N9qo8uLOickgx2ZMRZoMye...  (60자)
 │  │  └ 솔트 + 해시
 │  └ 강도 (2^10번 반복)
 └ 버전
```

`password_hash VARCHAR(100)`은 60자 해시를 담기에 넉넉한 크기입니다.

## Spring Security 전체가 아니라 BCrypt만

`pom.xml`

```xml
<!-- BCrypt만 사용 -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-core</artifactId>
    <version>5.6.10</version>
</dependency>
```

Spring Security는 로그인 화면, 세션 관리, 권한 검사까지 해 주는 큰 프레임워크입니다. Fashion Archive는 로그인을 [세션과 인터셉터](/posts/fashion-archive-auth-session-login)로 직접 만들고, Spring Security에서는 **비밀번호 해시 기능만** 가져옵니다. `spring-security-core`만 의존성에 넣고, 보안 필터 체인(`spring-security-web`, `spring-security-config`)은 쓰지 않습니다.

`spring-security-core` 5.6.10은 Spring 5 계열이라 eGovFrame 4.1의 Spring 5.3과 함께 씁니다.

## 빈으로 등록

`context-datasource.xml`

```xml
<bean id="passwordEncoder" class="org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder" />
```

`CmmnApiController`

```java
@Autowired
private PasswordEncoder passwordEncoder;   // context-datasource.xml의 BCrypt 빈
```

필드 타입은 구현 클래스(`BCryptPasswordEncoder`)가 아니라 인터페이스(`PasswordEncoder`)입니다. 컨트롤러는 "비밀번호를 해시하고 비교하는 것"이라는 역할만 알고, 어떤 알고리즘인지는 XML의 빈 정의가 정합니다.

## encode와 matches

```java
// 가입
user.put("passwordHash", passwordEncoder.encode(password));

// 로그인
passwordEncoder.matches(password, (String) user.get("password_hash"))
```

| 메서드 | 하는 일 |
|---|---|
| `encode(원문)` | 새 솔트를 만들어 해시. 같은 원문도 부를 때마다 다른 결과 |
| `matches(원문, 저장된 해시)` | 저장된 해시에서 솔트를 꺼내 같은 방식으로 해시해 비교 |

`encode` 결과가 매번 다르므로 "입력을 해시해서 DB 값과 `equals`로 비교"하면 항상 틀립니다. 비교는 반드시 `matches`로 합니다.

### 72바이트

BCrypt는 입력의 앞 72바이트만 씁니다. 이 버전의 `BCryptPasswordEncoder`는 더 긴 입력을 받아도 앞 72바이트만으로 해시하므로, 가입 API가 72바이트를 넘는 비밀번호를 미리 막습니다([가입](/posts/fashion-archive-auth-signup)).

## PasswordHashTool

`src/test/java/com/fashion/PasswordHashTool.java`

```java
public class PasswordHashTool {

    public static void main(String[] args) {
        System.out.print("비밀번호 입력: ");
        String raw = new Scanner(System.in).nextLine();
        System.out.println(new BCryptPasswordEncoder().encode(raw));
    }
}
```

비밀번호를 입력하면 BCrypt 해시를 출력하는 작은 프로그램입니다. 앱의 가입 화면을 거치지 않고 DB에 계정을 직접 넣을 때 `password_hash` 값을 만드는 용도입니다. 테스트 폴더(`src/test/java`)에 두어 war에 들어가지 않습니다. IntelliJ에서 `main`을 실행하면 됩니다.

## 정리

- 비밀번호는 솔트가 섞인 느린 해시인 BCrypt로 저장합니다.
- Spring Security는 `spring-security-core`의 `BCryptPasswordEncoder`만 빈으로 쓰고, 로그인 흐름은 세션과 인터셉터로 직접 만듭니다.
- 해시는 `encode`, 비교는 `matches`로 하고, 72바이트를 넘는 비밀번호는 가입 때 막습니다.
- `PasswordHashTool`은 계정을 DB에 직접 넣을 때 쓸 해시를 만듭니다.
