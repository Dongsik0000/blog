---
title: "입력 검증과 오류 응답"
slug: "fashion-archive-spring-mvc-validation"
category: "development"
publishedAt: "2026-10-08"
summary: "사용자에게 보여 줄 문장을 담은 예외를 던지는 Validation 유틸리티, API 메서드의 try/catch가 그 문장을 그대로 응답하고 그 밖의 오류는 로그와 일반 문장으로 나누는 방식, 오류 화면 설정을 설명합니다."
series: "fashion-archive"
chapter: "spring-mvc"
seriesOrder: 6
---
## 이 글에서 할 일

사용자가 제목을 비워 두거나, 너무 긴 메모를 넣거나, 이상한 링크 주소를 넣으면 어떻게 될까요? Fashion Archive는 입력 검증을 `Validation` 클래스에 모으고, 검증 실패를 **사용자에게 보여 줄 문장**으로 돌려줍니다.

## Validation: 실패하면 문장을 담은 예외

`com/fashion/cmmn/util/Validation.java`

```java
// 입력 검증. 실패 시 사용자에게 그대로 보여줄 메시지를 담은 IllegalArgumentException을 던진다
public final class Validation {

    public static String requireText(Object value, String field, int max) {
        String s = value == null ? "" : value.toString().trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException(field + "을(를) 입력해주세요.");
        }
        if (s.length() > max) {
            throw new IllegalArgumentException(field + "은(는) " + max + "자 이하로 입력해주세요.");
        }
        return s;
    }

    public static String optionalText(Object value, String field, int max) {
        String s = value == null ? "" : value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return requireText(s, field, max);
    }
}
```

| 메서드 | 검사 | 통과하면 |
|---|---|---|
| `requireText(값, "제목", 100)` | 비었거나 100자 초과면 실패 | 앞뒤 공백을 뺀 문자열 |
| `optionalText(값, "메모", 2000)` | 비었으면 통과, 있으면 길이 검사 | 문자열, 비었으면 `null` |
| `requireHttpUrl(값)` | `http`, `https`이고 호스트가 있어야 함 | 주소 |
| `requireLoginId(값)` | 영문 소문자, 숫자, `_`, `-` 3~30자 | 아이디 |
| `imageExtension(앞 12바이트)` | jpg, png, webp 파일 머리인지 | `"jpg"` 등 |

**검사하면서 값을 다듬어 돌려줍니다.** 호출하는 쪽은 한 줄로 "꺼내기 + 검사 + 정리"를 끝냅니다.

```java
String cleanTitle = Validation.requireText(title, "제목", 100);
String cleanMemo = Validation.optionalText(memo, "메모", 2000);
```

빈 메모는 `null`로 바꿔 DB에 빈 문자열 대신 `NULL`이 들어가게 합니다.

## API에서 받기

`PhotoApiController.update` (일부)

```java
try {
    String title = Validation.requireText(param.get("title"), "제목", 100);
    String memo = Validation.optionalText(param.get("memo"), "메모", 2000);
    List<Integer> ids = requireCategories(toIntList(param.get("categoryIds")));
    ...
    return Response.of(Constants.SUCCESS);
} catch (IllegalArgumentException e) {
    return Response.of(Constants.FAIL, e.getMessage(), null);
} catch (Exception e) {
    logger.error("사진 수정 중 오류", e);
    return Response.of(Constants.FAIL, "사진을 수정하지 못했습니다.", null);
}
```

예외를 두 종류로 나눕니다.

| 예외 | 원인 | 응답 문장 | 로그 |
|---|---|---|---|
| `IllegalArgumentException` | 사용자가 고칠 수 있는 입력 문제 | `Validation`이 만든 문장 그대로 ("제목을(를) 입력해주세요.") | 남기지 않음 |
| 그 밖의 `Exception` | 서버나 DB 문제 | 그 기능의 일반 문장 ("사진을 수정하지 못했습니다.") | 스택 트레이스와 함께 `error` |

입력 실수를 서버 오류로 기록하지 않으므로 로그에는 진짜 문제만 남습니다. 반대로 서버 오류의 자세한 내용(SQL, 클래스 이름)은 화면에 나가지 않습니다.

### 직접 만든 검증도 같은 방식

```java
// 최소 1개, 중복 제거
private static List<Integer> requireCategories(List<Integer> ids) {
    if (ids == null || ids.isEmpty()) {
        throw new IllegalArgumentException("카테고리를 하나 이상 선택해주세요.");
    }
    return new ArrayList<Integer>(new LinkedHashSet<Integer>(ids));
}
```

컨트롤러 안의 검증도 `IllegalArgumentException`에 문장을 담아 던지므로 같은 `catch`에서 처리됩니다. `LinkedHashSet`으로 중복 id를 빼면서 고른 순서는 유지합니다. 같은 카테고리가 두 번 오면 `photo_category`의 기본 키 때문에 INSERT가 실패하므로 미리 정리합니다.

## 화면 JS에서 받기

```js
handleSubmitResult = function(res){
    if(res.code !== '00'){
        App.error('저장 실패', res.message);
        return;
    }
    ...
};
```

화면은 `code`가 `"00"`이 아니면 서버가 보낸 `message`를 화면 아래 알림(토스트)으로 보여 줍니다. 서버가 사용자용 문장을 만들어 보내므로 화면 JS가 경우마다 문장을 정할 필요가 없습니다.

화면 JS도 보내기 전에 같은 검사를 한 번 합니다(제목, 카테고리, 파일 크기). 서버까지 가지 않고 바로 알려 주기 위한 것이고, 최종 검사는 항상 서버의 `Validation`입니다.

## 화면 요청의 오류

API가 아닌 화면 요청의 404와 500은 `web.xml`의 `error-page`가 `/WEB-INF/jsp/error.jsp`로 보여 줍니다([web.xml](/posts/fashion-archive-spring-mvc-web-xml)).

## 정리

- `Validation`은 검사하면서 값을 다듬어 돌려주고, 실패하면 사용자용 문장을 담은 `IllegalArgumentException`을 던집니다.
- API는 그 예외는 문장 그대로, 그 밖의 예외는 로그를 남기고 일반 문장으로 응답합니다.
- 화면 JS는 `message`를 그대로 보여 주고, 서버의 검증이 최종 기준입니다.
