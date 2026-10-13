---
title: "fetch로 JSON 주고받을 때 놓치기 쉬운 세 가지"
slug: "sample-fetch-json"
category: "development"
publishedAt: "2026-10-05"
summary: "fetch는 HTTP 오류에서 실패하지 않는다는 점부터, Content-Type과 응답 코드 설계까지 정리했습니다."
coverPrompt: "Clean flat illustration of two speech bubbles exchanging curly braces, warm grey background with one orange accent, no text"
hashtags: ["JavaScript", "fetch", "개발기록", "DevGear"]
---

## 1. 404나 500이어도 fetch는 성공한다

`fetch`가 반환하는 Promise는 **네트워크가 끊겼을 때만** 실패합니다. 서버가 404나 500을 보내도 정상 응답으로 처리되므로, `res.ok`를 직접 확인해야 합니다.

```javascript
fetch(url).then(function (res) {
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return res.json();
});
```

## 2. 보낼 때는 Content-Type을 맞춘다

본문을 `JSON.stringify`로 보낼 때는 `Content-Type: application/json` 헤더를 함께 보내야 서버가 JSON으로 읽습니다. 빠뜨리면 서버에서 **415 Unsupported Media Type**을 받게 됩니다.

## 3. 성공과 실패를 응답 코드로 구분한다

HTTP 상태와 별개로, 응답 본문에 `code`를 두면 화면에서 경우를 나누기 쉽습니다.

- `00` 성공
- `90` 입력 오류 (문구를 그대로 화면에 표시)
- `99` 서버 오류 (일반적인 문구만 표시)

> 배운 점: 서버 내부 오류 내용은 *화면에 그대로 보여 주지 않고* 로그에만 남겨야 합니다.
