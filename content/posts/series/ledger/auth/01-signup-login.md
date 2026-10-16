---
title: "초대 코드 가입과 BCrypt 로그인"
slug: "ledger-auth-signup-login"
category: "development"
publishedAt: "2026-10-08"
summary: "초대 코드가 있어야 가입하는 구조, BCrypt로 비밀번호를 저장하는 이유와 72바이트 제한, 아이디 존재 여부가 드러나지 않게 하는 로그인 응답과 더미 해시, 상수 시간 비교를 실제 코드로 설명합니다."
series: "ledger"
chapter: "auth"
seriesOrder: 1
---
## 이 글에서 할 일

Ledger는 Spring Security 없이 로그인을 직접 만들었습니다. `pom.xml`에도 그 결정이 적혀 있습니다.

```xml
<!-- 비밀번호 해시(BCrypt)만 사용. 인증은 인터셉터 + 세션으로 직접 처리 -->
```

직접 만들면 구조를 다 이해할 수 있는 대신, 보안 구멍도 직접 막아야 합니다. 이 글에서는 가입과 로그인에서 막은 구멍들을 하나씩 봅니다.

## 가입: 초대 코드

가계부는 개인 기록이라 아무나 가입할 이유가 없습니다. 가입하려면 서버에 정해 둔 **초대 코드**를 알아야 합니다.

`AuthApiController.signup` (순서만)

```java
// 1. 입력 검사: 아이디 50자 이하, 비밀번호 8자 이상 72바이트 이하, 비밀번호 확인 일치
// 2. 시도 제한: 같은 IP에서 5번 틀리면 5분 차단
if (!loginAttemptLimiter.tryAcquire(attemptKey)) {
    return Response.of(Constants.SIGNUP_BLOCKED);
}
// 3. 코드 검사를 중복 검사보다 먼저 — 코드가 없는 사람이 아이디 존재 여부를 알아낼 수 없게
if (!isSignupCodeValid(code)) {
    return Response.of(Constants.SIGNUP_FAIL_CODE);
}
loginAttemptLimiter.reset(attemptKey);
// 4. 아이디 중복 검사
if (authService.selectUserByUsername(username) != null) {
    return Response.of(Constants.SIGNUP_FAIL_EXISTS);
}
// 5. 사용자 + 기본 카테고리 + 기본 결제수단을 한 트랜잭션으로
```

**순서가 보안입니다.** 아이디 중복 검사를 먼저 하면, 초대 코드가 없는 사람도 "이미 있는 아이디입니다" 응답으로 어떤 아이디가 있는지 알아낼 수 있습니다. 코드 검사를 먼저 두어 코드를 아는 사람만 그 다음 단계에 도달하게 했습니다.

### 상수 시간 비교

```java
// 환경변수 LEDGER_SIGNUP_CODE 가 비어 있으면 가입 불가. 비교는 상수 시간
private boolean isSignupCodeValid(String code) {
    if (signupCode == null || signupCode.isBlank()) {
        return false;
    }
    return MessageDigest.isEqual(signupCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
}
```

`String.equals`는 첫 글자부터 비교하다 **다른 글자를 만나면 바로** 끝납니다. 그래서 앞부분이 맞을수록 응답이 아주 조금 늦어지고, 이 차이를 수없이 재면 코드를 한 글자씩 알아낼 수 있습니다(**타이밍 공격**). `MessageDigest.isEqual`은 끝까지 비교하므로 걸리는 시간이 같습니다.

## 비밀번호 저장: BCrypt

```java
user.put("passwordHash", passwordEncoder.encode(password));
```

비밀번호는 **원문을 저장하지 않고**, 되돌릴 수 없는 해시만 저장합니다. DB가 유출되어도 원래 비밀번호를 바로 알 수 없게 하려는 것입니다.

| 방식 | 문제 |
|---|---|
| 원문 저장 | 유출되면 끝 |
| SHA-256 같은 빠른 해시 | 너무 빨라서 초당 수십억 번 대입해 볼 수 있음. 같은 비밀번호는 같은 해시라 미리 계산한 표로 찾을 수 있음 |
| **BCrypt** | 일부러 느림(대입 공격이 어려움). 해시마다 무작위 **솔트**가 섞여 같은 비밀번호도 매번 다른 해시 |

BCrypt 해시에는 솔트와 반복 횟수가 함께 들어 있어서, 확인할 때는 `passwordEncoder.matches(입력, 저장된해시)`만 부르면 됩니다.

### 72바이트 제한

```java
private static final int PASSWORD_MAX_BYTES = 72; // BCrypt 입력 한도. 넘으면 encode/matches 가 예외를 던진다

private boolean fitsBcrypt(String password) {
    return !password.isEmpty() && password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
}
```

BCrypt는 입력을 72**바이트**까지만 씁니다. 주석대로 이 버전의 `BCryptPasswordEncoder`는 72바이트를 넘으면 예외를 던집니다. 한글은 한 글자가 UTF-8로 3바이트라 **24글자**면 한도에 닿습니다. 글자 수가 아니라 바이트 수로 검사해, 긴 비밀번호가 500 오류가 되는 대신 입력 오류 안내가 나가게 했습니다.

## 로그인

`AuthApiController.login` (일부)

```java
Map<String, Object> user = username.isEmpty() || username.length() > USERNAME_MAX
        ? null
        : authService.selectUserByUsername(username);
// resultType=HashMap 이라 키는 컬럼명 그대로(password_hash)
String hash = user == null ? dummyHash : (String) user.get("password_hash");
boolean matched = fitsBcrypt(password) && passwordEncoder.matches(password, hash);

if (user == null || !matched) {
    return Response.of(Constants.LOGIN_FAIL);
}

loginAttemptLimiter.reset(attemptKey);
SessionUtil.login(request, ((Number) user.get("id")).longValue(), (String) user.get("username"));
return Response.of(Constants.SUCCESS);
```

### 응답으로 아이디 존재 여부를 흘리지 않기

```java
// 로그인 결과 코드. 계정 없음과 비밀번호 오류를 구분하지 않는다(아이디 존재 여부 노출 방지)
public static final String LOGIN_FAIL = "01";
```

"없는 아이디입니다"와 "비밀번호가 틀렸습니다"를 나눠 알려 주면, 공격자는 먼저 있는 아이디를 찾고 그 아이디에만 비밀번호를 대입합니다. 둘 다 같은 코드(`01`)로 답합니다.

### 응답 시간으로도 흘리지 않기: 더미 해시

```java
// 없는 아이디도 BCrypt 비교를 한 번 해서 응답 시간으로 아이디 존재 여부가 드러나지 않게 한다
private String dummyHash;

@PostConstruct
public void init() {
    dummyHash = passwordEncoder.encode("ledger-dummy-password");
}
```

BCrypt는 일부러 느립니다. 없는 아이디일 때 비교를 건너뛰면 응답이 눈에 띄게 빨라져서, 응답 코드를 같게 해도 **시간**으로 아이디 존재 여부가 드러납니다. 없는 아이디도 미리 만든 더미 해시와 한 번 비교해 걸리는 시간을 맞춥니다.

## 정리

- 가입은 초대 코드가 필요하고, 코드 검사를 아이디 중복 검사보다 먼저 해 아이디 목록이 새지 않게 합니다.
- 초대 코드는 `MessageDigest.isEqual`로 상수 시간 비교합니다.
- 비밀번호는 BCrypt 해시로 저장하고, 72바이트 한도를 바이트 단위로 검사합니다.
- 로그인 실패는 계정 없음과 비밀번호 오류를 같은 코드로 답하고, 더미 해시로 응답 시간도 맞춥니다.
