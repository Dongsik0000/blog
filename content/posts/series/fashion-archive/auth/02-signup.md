---
title: "가입 코드 회원가입과 시도 제한"
slug: "fashion-archive-auth-signup"
category: "development"
publishedAt: "2026-10-08"
summary: "가입 코드를 아는 사람만 가입하게 하는 회원가입 API의 검사 순서, 아이디 규칙, 72바이트 비밀번호 한도, 상수 시간 비교, 같은 IP의 5회 실패 5분 차단, nginx 뒤에서 실제 IP를 구하는 RequestUtil을 설명합니다."
series: "fashion-archive"
chapter: "auth"
seriesOrder: 2
---
## 이 글에서 할 일

처음 설계는 관리자 한 명만 쓰는 사이트였고, 커밋 `38f6f95`에서 **가입 코드가 있어야 하는 회원가입**이 들어왔습니다. 이 글에서는 가입 API가 무엇을 어떤 순서로 검사하는지 봅니다.

## 검사 순서

`CmmnApiController.signup` (일부)

```java
// 로그인과 같은 방식(앞뒤 공백 제거)으로 읽어야 가입한 비밀번호로 로그인된다
String loginId = Validation.requireLoginId(param.get("loginId"));
String password = Validation.requireText(param.get("password"), "비밀번호", 200);
if (password.length() < PASSWORD_MIN || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
    throw new IllegalArgumentException("비밀번호는 8자 이상, 72바이트 이하로 입력해주세요. (영문 72자, 한글 24자까지)");
}
if (!password.equals(Validation.requireText(param.get("passwordConfirm"), "비밀번호 확인", 200))) {
    throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
}

String attemptKey = "signup|" + RequestUtil.clientIp(request);
if (!loginAttemptLimiter.tryAcquire(attemptKey)) {
    return Response.of(Constants.FAIL, "가입 코드를 여러 번 잘못 입력했습니다. 5분 뒤에 다시 시도해주세요.", null);
}
// 코드 검사를 중복 검사보다 먼저: 코드가 없는 사람이 아이디 존재 여부를 알아낼 수 없게
if (!isSignupCodeValid(param.get("signupCode"))) {
    return Response.of(Constants.FAIL, "가입 코드가 올바르지 않습니다.", null);
}
loginAttemptLimiter.reset(attemptKey);

Map<String, Object> user = new HashMap<String, Object>();
user.put("loginId", loginId);
user.put("passwordHash", passwordEncoder.encode(password));
try {
    cmmnService.insertUser(user);
} catch (DuplicateKeyException e) {
    return Response.of(Constants.FAIL, "이미 사용 중인 아이디입니다.", null);
}
```

```text
1. 입력 형식 (아이디 규칙, 비밀번호 길이, 확인 일치)
2. 시도 제한 (같은 IP 5번 → 5분 차단)
3. 가입 코드
4. 아이디 중복 (DB UNIQUE 제약)
5. 저장 (BCrypt 해시)
```

## 1. 아이디 규칙

```java
// 아이디는 사진 폴더 이름(/srv/fashion-uploads/{아이디})으로도 쓰인다. 경로 문자(/, .)가 들어갈 수 없게 제한한다
private static final Pattern LOGIN_ID = Pattern.compile("[a-z0-9_-]{3,30}");
```

영문 소문자, 숫자, `_`, `-`로 3~30자만 됩니다. 아이디가 사진 폴더 이름이 되므로 `/`, `.`처럼 경로를 바꾸는 글자를 막습니다([디스크 저장](/posts/fashion-archive-upload-local-storage)). 대문자도 받지 않아 `Minji`와 `minji`가 다른 폴더가 되는 일이 없습니다.

## 2. 비밀번호 길이: 8자 이상, 72바이트 이하

```java
private static final int PASSWORD_MAX_BYTES = 72;   // BCrypt 입력 한도. 넘는 부분은 비교에 쓰이지 않는다
```

BCrypt는 입력의 앞 72**바이트**만 씁니다. 그보다 긴 비밀번호는 뒷부분이 달라도 같은 비밀번호로 취급됩니다. 그래서 가입할 때 72바이트를 넘지 않게 막습니다. 한글은 한 글자가 UTF-8로 3바이트라, 안내 문장에 "영문 72자, 한글 24자까지"라고 함께 적었습니다.

비밀번호는 로그인과 같은 방식(`requireText`, 앞뒤 공백 제거)으로 읽습니다. 주석대로, 가입과 로그인이 다르게 읽으면 가입한 비밀번호로 로그인되지 않기 때문입니다.

## 3. 가입 코드: 상수 시간 비교

```java
// FASHION_SIGNUP_CODE가 비어 있으면 가입 불가. 비교는 상수 시간
private boolean isSignupCodeValid(Object value) {
    String code = value == null ? "" : value.toString().trim();
    if (signupCode == null || signupCode.isBlank()) {
        return false;
    }
    return MessageDigest.isEqual(signupCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
}
```

- 가입 코드는 서버의 환경 변수 `FASHION_SIGNUP_CODE`에 둡니다. **비어 있으면 아무도 가입할 수 없습니다.** 설정을 빠뜨려도 열리는 쪽이 아니라 닫히는 쪽입니다.
- `String.equals`는 다른 글자를 만나면 바로 끝나서, 앞부분이 맞을수록 응답이 아주 조금 늦습니다. 이 차이로 코드를 한 글자씩 알아내는 **타이밍 공격**을 막으려고, 끝까지 비교하는 `MessageDigest.isEqual`을 씁니다.

### 코드 검사를 중복 검사보다 먼저

아이디 중복을 먼저 검사하면, 코드가 없는 사람도 "이미 사용 중인 아이디입니다" 응답으로 가입된 아이디를 알아낼 수 있습니다. 코드를 아는 사람만 중복 검사에 도달하게 합니다.

## 4. 아이디 중복: DB 제약에 맡기기

```java
try {
    cmmnService.insertUser(user);
} catch (DuplicateKeyException e) {
    return Response.of(Constants.FAIL, "이미 사용 중인 아이디입니다.", null);
}
```

"이 아이디가 있나?"를 먼저 SELECT로 확인하지 않고, 바로 INSERT합니다. 같은 아이디가 있으면 `users.login_id`의 `UNIQUE` 제약이 거부하고, Spring이 그 오류를 `DuplicateKeyException`으로 바꿔 줍니다. 두 사람이 동시에 같은 아이디로 가입해도 DB가 하나만 받아 줍니다.

```xml
<!-- 회원가입. 가입자는 USER로 넣어 기존 관리자(ADMIN)와 구분한다. 아이디 중복은 UNIQUE 제약이 막는다 -->
<insert id="insertUser" parameterType="java.util.HashMap">
    INSERT INTO users (login_id, password_hash, role)
    VALUES (#{loginId}, #{passwordHash}, 'USER')
</insert>
```

## 시도 제한: LoginAttemptLimiter

```java
String attemptKey = "signup|" + RequestUtil.clientIp(request);
if (!loginAttemptLimiter.tryAcquire(attemptKey)) { ... }
...
loginAttemptLimiter.reset(attemptKey);   // 코드가 맞으면 기록을 지움
```

같은 IP에서 가입 코드를 5번 틀리면 5분 동안 막습니다. 가입 코드를 무작위로 맞혀 보는 공격을 막습니다. `LoginAttemptLimiter`와 `RequestUtil`은 같은 서버의 Ledger에서 만든 코드를 옮겨 온 것이고(커밋 `38f6f95`), 동시에 몰려온 요청도 5번까지만 통과시키는 구조는 [Ledger의 시도 제한 글](/posts/ledger-auth-login-limit)에서 자세히 다룹니다. Fashion Archive에도 같은 테스트(`LoginAttemptLimiterTest`)가 있습니다.

### nginx 뒤에서 실제 IP 구하기

```java
// 클라이언트 IP. 서버에서 Tomcat은 127.0.0.1에만 열려 있고 nginx가 X-Real-IP를 넣는다.
// 그래서 루프백에서 온 요청일 때만 X-Real-IP를 믿는다.
static String clientIp(String remoteAddr, String realIpHeader) {
    boolean loopback = "127.0.0.1".equals(remoteAddr)
            || "0:0:0:0:0:0:0:1".equals(remoteAddr)
            || "::1".equals(remoteAddr);
    if (loopback && realIpHeader != null) {
        String realIp = realIpHeader.trim();
        if (!realIp.isEmpty() && realIp.length() <= IP_MAX_LENGTH) {
            return realIp;
        }
    }
    return remoteAddr;
}
```

Tomcat 앞에는 nginx가 있어서, Tomcat이 보는 접속 주소는 항상 `127.0.0.1`(nginx)입니다. 그대로 쓰면 모든 방문자가 같은 IP가 되어, 한 사람이 5번 틀리면 모두가 막힙니다. nginx가 넣어 주는 `X-Real-IP` 헤더로 실제 IP를 구합니다.

단, 이 헤더는 누구나 요청에 넣을 수 있습니다. **요청이 서버 자신(nginx)에서 왔을 때만** 헤더를 믿어, 외부에서 헤더로 IP를 속여 제한을 피하지 못하게 합니다. 너무 긴 값은 버립니다. `RequestUtilTest`가 네 경우를 검사합니다.

## 정리

- 가입은 형식 → 시도 제한 → 가입 코드 → 중복 → 저장 순서이고, 코드 검사를 중복 검사보다 먼저 합니다.
- 아이디는 폴더 이름이 되므로 경로 문자를 막고, 비밀번호는 BCrypt 한도에 맞춰 72바이트로 제한합니다.
- 가입 코드는 비어 있으면 가입을 막고, 상수 시간으로 비교합니다.
- 중복은 DB의 UNIQUE 제약에 맡기고, 같은 IP의 반복 실패는 5분 막습니다.
