---
title: "세션 로그인 (loginId와 userId)"
slug: "fashion-archive-auth-session-login"
category: "development"
publishedAt: "2026-10-08"
summary: "로그인 API가 아이디와 비밀번호를 확인한 뒤 세션에 loginId와 userId 두 값을 넣는 이유, 계정 없음과 비밀번호 오류를 같은 문장으로 답하는 방법, 화면 JS의 로그인 흐름, 로그아웃을 설명합니다."
series: "fashion-archive"
chapter: "auth"
seriesOrder: 1
---
## 이 글에서 할 일

Fashion Archive는 **서버 세션**으로 로그인을 유지합니다. 로그인에 성공하면 세션에 사용자 정보를 넣고, 이후 요청은 [인터셉터](/posts/fashion-archive-spring-mvc-interceptor)가 그 값을 확인합니다. 이 글에서는 로그인 API를 한 단계씩 봅니다.

## 화면: 로그인 폼

`resources/js/app/login/login.js` (일부)

```js
submitLogin = function(){
    if(settings.submitting) return;                    /* 이미 요청 중이면 무시 */

    var param = App.formToObject(m$.loginForm);         /* {loginId: '...', password: '...'} */
    if(App.isEmpty(m$.useridInput.value.trim()) || App.isEmpty(m$.passwordInput.value.trim())){
        App.error('알림', '아이디와 비밀번호를 입력해주세요.');
        return;
    }

    settings.submitting = true;
    App.post(url.login, param)
        .then(handleLoginResult)
        .catch(function(){})
        .then(function(){ settings.submitting = false; });
},
```

`App.formToObject`는 폼의 입력 칸을 `name` 속성을 키로 하는 객체로 바꿉니다. JSP의 `<input name="loginId">`가 서버의 `param.get("loginId")`로 이어집니다. 로그인에 성공하면 `next`(원래 가려던 화면)로 이동합니다.

## 서버: 로그인 API

`com/fashion/cmmn/controller/CmmnApiController.java`

```java
@ResponseBody
@PostMapping("/api/login")
public Response login(@RequestBody Map<String, Object> param, HttpSession session) {
    String code;
    String message = null;

    try {
        // 1) 파라미터. JS가 보낸 JSON 키 = JSP input의 name
        String loginId = Validation.requireText(param.get("loginId"), "아이디", 50);
        String password = Validation.requireText(param.get("password"), "비밀번호", 200);

        // 2) 조회
        Map<String, Object> user = cmmnService.selectUserByLoginId(loginId);

        // 3) 비교. 계정이 없을 때와 비밀번호가 틀렸을 때 메시지를 같게 해서 계정 존재 여부를 노출하지 않는다
        if (user == null || !passwordEncoder.matches(password, (String) user.get("password_hash"))) {
            code = Constants.LOGIN_FAIL;
            message = "아이디 또는 비밀번호를 확인해주세요.";
        } else {
            // 4) 세션 저장. 인터셉터와 레이아웃 JSP가 이 키를 본다
            session.setAttribute(Constants.SESSION_LOGIN_ID, user.get("login_id"));
            session.setAttribute(Constants.SESSION_USER_ID, ((Number) user.get("id")).longValue());
            code = Constants.SUCCESS;
        }
    } catch (IllegalArgumentException e) {
        code = Constants.FAIL;
        message = e.getMessage();              // Validation이 만든 사용자용 문구
    } catch (Exception e) {
        logger.error("로그인 처리 중 오류", e);
        code = Constants.FAIL;
        message = "로그인 처리 중 오류가 발생했습니다.";
    }

    return Response.of(code, message, null);
}
```

주석의 번호대로 네 단계입니다.

| 단계 | 하는 일 |
|---|---|
| 1) 파라미터 | 비었거나 너무 길면 `Validation`이 사용자용 문장으로 실패 |
| 2) 조회 | 아이디로 사용자 한 명 (`users.login_id`는 UNIQUE) |
| 3) 비교 | `passwordEncoder.matches(입력, 저장된 해시)`로 BCrypt 비교 |
| 4) 세션 저장 | `loginId`와 `userId`를 세션에 |

### 계정 없음과 비밀번호 오류를 같은 문장으로

```java
if (user == null || !passwordEncoder.matches(password, (String) user.get("password_hash"))) {
    code = Constants.LOGIN_FAIL;
    message = "아이디 또는 비밀번호를 확인해주세요.";
}
```

"없는 아이디입니다"와 "비밀번호가 틀렸습니다"를 나눠 알려 주면, 어떤 아이디가 가입되어 있는지 알아낼 수 있습니다. 두 경우를 같은 코드(`01`)와 같은 문장으로 답합니다.

### 세션에 두 값을 넣는 이유

```java
public static final String SESSION_LOGIN_ID = "loginId";
public static final String SESSION_USER_ID = "userId";
```

| 키 | 값 | 쓰는 곳 |
|---|---|---|
| `loginId` | 로그인 아이디 (글자) | 인터셉터의 로그인 확인, 레이아웃 JSP의 로그인 상태 표시, **사진 폴더 이름** |
| `userId` | `users.id` (숫자) | 모든 사진 SQL의 `owner_id` 조건 |

사진은 DB에서는 숫자 id(`owner_id`)로, 디스크에서는 아이디 이름의 폴더(`/{아이디}/`)로 사용자를 구분합니다([디스크 저장](/posts/fashion-archive-upload-local-storage)). 두 곳에서 각각 필요한 값을 로그인할 때 한 번 넣어 두고, 요청마다 DB에서 다시 찾지 않습니다.

JSP는 EL로 세션 값을 바로 읽습니다.

```jsp
var isAdmin = ${not empty sessionScope.loginId};
```

## 로그인 화면에 들어올 때

`CmmnController`

```java
@GetMapping("/login")
public String login(HttpSession session) {
    if (session.getAttribute(Constants.SESSION_LOGIN_ID) != null) {
        return "redirect:/";
    }
    return "login/loginMain";
}
```

이미 로그인했으면 로그인 화면 대신 홈으로 보냅니다. 가입 화면도 같습니다.

## 로그아웃

```java
@GetMapping("/logout")
public String logout(HttpSession session) {
    session.invalidate();
    return "redirect:/";
}
```

`session.invalidate()`는 서버의 세션을 통째로 지웁니다. 세션 ID가 남아 있어도 더는 로그인 상태가 아닙니다. 홈(`/`)으로 보내면 인터셉터가 로그인 화면으로 다시 보냅니다. 레이아웃 헤더의 "로그아웃" 링크가 이 주소입니다.

## 세션 쿠키

세션 ID는 `JSESSIONID` 쿠키로 오갑니다. `web.xml`의 설정으로 JavaScript에서 읽을 수 없고(`http-only`), 주소에 붙지 않으며(`tracking-mode COOKIE`), 60분 동안 요청이 없으면 끝납니다. `SameSite` 설정은 [CSRF 글](/posts/fashion-archive-auth-csrf-url)에서 봅니다.

## 정리

- 로그인 API는 검증 → 조회 → BCrypt 비교 → 세션 저장 순서이고, 실패는 계정 존재 여부가 드러나지 않는 같은 문장으로 답합니다.
- 세션에는 폴더 이름에 쓰는 `loginId`와 SQL 조건에 쓰는 `userId`를 함께 넣습니다.
- 이미 로그인했으면 로그인 화면 대신 홈으로 보내고, 로그아웃은 `invalidate()`로 세션을 지웁니다.
