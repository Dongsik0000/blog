---
title: "휴대폰 화면과 홈 화면 아이콘"
slug: "fashion-archive-view-mobile"
category: "development"
publishedAt: "2026-10-08"
summary: "viewport 설정, 등록 폼의 사진 미리보기(URL.createObjectURL), 홈 화면에 앱처럼 추가하는 manifest.json과 상대 경로, 로그인 전에도 읽혀야 하는 manifest 위치를 설명합니다."
series: "fashion-archive"
chapter: "view"
seriesOrder: 5
---
## 이 글에서 할 일

옷을 입고 찍은 사진은 대부분 휴대폰에 있습니다. 그래서 사진 등록과 보기를 휴대폰 브라우저에서 바로 할 수 있게 했습니다. 이 글에서는 휴대폰에서 쓰기 위한 설정을 봅니다.

## viewport

```html
<meta name="viewport" content="width=device-width, initial-scale=1.0">
```

레이아웃과 로그인 화면 모두에 있습니다. 휴대폰 브라우저가 PC 폭으로 그린 뒤 축소하지 않고, 기기 폭에 맞춰 그리게 합니다.

![휴대폰 폭에서 본 사진 목록. 메뉴가 두 줄로 나뉘고 사진 카드가 두 열로 놓여 있다](/uploads/capture-fashion-09-mobile-list.jpg)

![휴대폰 폭에서 본 상세 화면. 사진 아래로 제목, 메모, 아이템별 제품 링크가 이어진다](/uploads/capture-fashion-10-mobile-detail.jpg)

## 사진 미리보기

등록 폼에서 사진을 고르면 올리기 전에 미리보기를 보여 줍니다(커밋 `43824d6` "upload preview").

`resources/js/app/photo/photoForm.js` (일부)

```js
m$.preview.src = URL.createObjectURL(file);
```

`URL.createObjectURL(file)`은 사용자가 고른 파일을 가리키는 **브라우저 안의 임시 주소**(`blob:...`)를 만듭니다. 파일을 서버에 올리지 않고도 `<img>`에 바로 보여 줄 수 있습니다. 휴대폰 사진첩에서 고른 사진이 맞는지 올리기 전에 확인할 수 있습니다.

파일을 고를 때 크기도 바로 확인합니다.

```js
if(file.size > settings.maxBytes){
```

`settings.maxBytes`는 주석에 적힌 대로 `web.xml`의 `max-file-size`와 같은 10MB입니다([multipart](/posts/fashion-archive-upload-multipart)).

![사진 올리기 화면. 파일을 고르자 코디 사진 미리보기가 나타나고, 아래에 제목, 메모, 카테고리 입력이 있다](/uploads/capture-fashion-02-upload-preview.jpg)

## 홈 화면에 추가: manifest.json

휴대폰 브라우저의 "홈 화면에 추가"를 누르면 앱처럼 아이콘이 생기고, 누르면 주소창 없이 열립니다(커밋 `803a4d8`).

`src/main/webapp/resources/manifest.json`

```json
{
  "name": "Fashion Archive",
  "short_name": "Fashion",
  "start_url": "../",
  "scope": "../",
  "display": "standalone",
  "background_color": "#F6F5F1",
  "theme_color": "#F6F5F1",
  "icons": [
    { "src": "images/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any" },
    { "src": "images/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any" }
  ]
}
```

| 항목 | 뜻 |
|---|---|
| `start_url: "../"` | 아이콘을 눌렀을 때 열 주소 |
| `scope: "../"` | 앱으로 볼 범위 |
| `display: standalone` | 주소창 없이 앱처럼 |
| `icons` | 192px, 512px PNG |

### 상대 경로를 쓴 이유

매니페스트 안의 상대 주소는 **매니페스트 파일의 위치**를 기준으로 풀립니다.

```text
매니페스트:  /p1/resources/manifest.json
"../"     → /p1/                       (start_url, scope)
"images/icon-192.png" → /p1/resources/images/icon-192.png
```

`/p1`을 직접 적지 않았으므로 다른 경로로 배포해도 고칠 필요가 없습니다.

### 로그인 전에도 읽혀야 하는 위치

```jsp
<%-- 홈 화면에 추가(웹 앱) 설정. 로그인 전에도 읽을 수 있게 /resources 아래에 둔다 --%>
<link rel="manifest" href="<c:url value='/resources/manifest.json'/>">
<link rel="apple-touch-icon" href="<c:url value='/resources/images/icon-180.png'/>">
```

[로그인 인터셉터](/posts/fashion-archive-spring-mvc-interceptor)는 `/resources/**`를 제외한 모든 요청에 로그인을 요구합니다. 매니페스트를 다른 곳에 두면 로그인하지 않은 상태의 브라우저가 매니페스트를 받지 못하고 로그인 화면 HTML을 받게 됩니다. 그래서 `/resources/` 아래에 둡니다.

iPhone은 매니페스트 아이콘 대신 `apple-touch-icon`(180px)을 씁니다.

<!-- 📷 스크린샷: 휴대폰 홈 화면의 Fashion Archive 아이콘 -->

## 정리

- `viewport`로 휴대폰 폭에 맞춰 그립니다.
- 등록 폼은 `URL.createObjectURL`로 올리기 전에 사진을 미리 보여 주고 크기를 바로 확인합니다.
- `manifest.json`은 자기 위치 기준의 상대 경로로 `/p1`에 묶이지 않게 하고, 로그인 없이 읽히도록 `/resources/` 아래에 둡니다.
