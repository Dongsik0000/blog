---
title: "휴대폰 화면과 홈 화면 아이콘"
slug: "ledger-view-mobile"
category: "development"
publishedAt: "2026-10-08"
summary: "하나의 화면을 PC와 휴대폰에서 함께 쓰기 위한 반응형 CSS, 숫자 키패드를 띄우는 inputmode, 홈 화면에 앱처럼 추가하는 manifest.json과 아이콘, 배포 후 예전 파일이 남지 않게 하는 캐시 설정을 설명합니다."
series: "ledger"
chapter: "view"
seriesOrder: 7
---
## 이 글에서 할 일

가계부는 지출한 그 자리에서 휴대폰으로 적어야 쓸모가 있습니다. Ledger는 앱을 따로 만들지 않고, **같은 웹 화면을 휴대폰에서도 쓰기 좋게** 만들었습니다.

## viewport: 휴대폰 화면 폭으로 그리기

`layout.tag`

```html
<meta name="viewport" content="width=device-width, initial-scale=1.0">
```

이 줄이 없으면 휴대폰 브라우저가 PC 화면 폭(보통 980px)으로 그린 뒤 축소해서, 글자가 아주 작게 보입니다. `width=device-width`는 "기기 화면 폭에 맞춰 그려라"는 뜻입니다.

## 반응형 CSS: 화면 폭에 따라 배치 바꾸기

`resources/css/style.css`는 **미디어 쿼리**로 화면 폭마다 배치를 바꿉니다.

| 폭 | 바뀌는 것 |
|---|---|
| 1150px 이하 | 여백과 칸 너비 조정 |
| 900px 이하 | 두 칸 배치를 한 칸으로 (대시보드, 차트, 설정 카드) |
| 768px 이하 | 왼쪽 세로 메뉴를 **위쪽 가로 메뉴**로, 넘치면 옆으로 밀기 |
| 480px, 380px 이하 | 버튼을 한 줄 전체로, 글자와 간격 축소 |

```css
@media (max-width: 768px) {
  .app-shell { display: block; }
  .app-sidebar { position: static; width: auto; height: auto; ... }
  .app-nav { flex-direction: row; gap: 5px; overflow-x: auto; ... }
}
```

휴대폰에서는 화면 왼쪽을 메뉴가 차지하면 본문이 좁아지므로, 메뉴를 위로 올리고 가로로 밀어 볼 수 있게 했습니다.

<!-- 📷 스크린샷: 휴대폰에서 본 대시보드 (위쪽 가로 메뉴) -->

큰 화면에서만 켜는 기능도 있습니다.

```css
@media (min-width: 901px) and (min-height: 860px) { .quick-entry { position: sticky; top: 24px; } }
```

대시보드의 빠른 입력 칸은 화면이 충분히 클 때만 스크롤을 따라 내려옵니다. 작은 화면에서 따라오면 본문을 가리기 때문입니다.

## 휴대폰 키보드: inputmode

`entryEditor.tag`

```html
<input type="text" id="${id}-amount" inputmode="numeric" autocomplete="off" placeholder="0" class="amount-input">
```

| 속성 | 뜻 |
|---|---|
| `inputmode="numeric"` | 휴대폰에서 **숫자 키패드**를 띄움 |
| `type="text"` | `number`가 아닌 이유: 입력하는 동안 `1,930,000`처럼 쉼표를 넣어 보여 주기 위해 |
| `autocomplete="off"` | 이전에 넣은 금액이 추천으로 뜨지 않게 |

금액 쉼표는 `App.bindAmountInput`이 입력할 때마다 넣고, 숫자가 아닌 글자가 들어오면 값을 바꾸지 않고 `aria-invalid`로 오류를 표시합니다.

## 홈 화면에 추가: manifest.json

휴대폰 브라우저의 "홈 화면에 추가"를 누르면 앱처럼 아이콘이 생기고, 누르면 주소창 없이 열립니다. 그 정보를 **웹 앱 매니페스트**에 적습니다.

`src/main/webapp/manifest.json`

```json
{
  "name": "가계부",
  "short_name": "가계부",
  "start_url": "./ledger/dashboard",
  "scope": "./",
  "display": "standalone",
  "background_color": "#f6f5ef",
  "theme_color": "#45654c",
  "icons": [
    { "src": "resources/images/icon.svg", "sizes": "any", "type": "image/svg+xml", "purpose": "any" },
    { "src": "resources/images/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any" },
    { "src": "resources/images/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any" }
  ]
}
```

| 항목 | 뜻 |
|---|---|
| `start_url` | 아이콘을 눌렀을 때 열 주소. 대시보드 |
| `scope` | 앱으로 볼 범위. `./`라서 `/p3/` 아래 전체 |
| `display: standalone` | 주소창 없이 앱처럼 |
| `theme_color` | 상태 표시줄 색 |
| `icons` | 기기마다 알맞은 크기를 고름 |

주소를 `./`처럼 **상대 경로**로 적어, `/p3`가 아닌 다른 경로로 배포해도 그대로 동작합니다.

iPhone은 매니페스트의 아이콘 대신 다음 줄을 봅니다.

```html
<link rel="apple-touch-icon" href="<c:url value='/resources/images/icon-180.png'/>">
```

그래서 180px PNG를 따로 두었습니다(커밋 "홈 화면 아이콘 추가: iOS용 apple-touch-icon(180)과 manifest PNG 아이콘(192·512)").

<!-- 📷 스크린샷: 휴대폰 홈 화면의 가계부 아이콘 -->

## 배포 후 예전 JS가 남지 않게

`dispatcher-servlet.xml`

```xml
<!-- 정적 자원은 no-cache: 브라우저가 보관하되 매번 변경 여부를 물어본다(바뀌지 않았으면 304).
     배포 후에도 예전 JS·CSS 가 남아 화면이 어긋나지 않게 한다 -->
<mvc:resources mapping="/resources/**" location="/resources/">
    <mvc:cache-control no-cache="true"/>
</mvc:resources>
```

브라우저는 한 번 받은 JS와 CSS를 저장해 두고 다시 씁니다. 배포로 서버의 응답 형식을 바꿨는데 브라우저에 예전 JS가 남아 있으면 화면이 어긋납니다. 설정 주석이 막으려는 상황이 이것입니다. `no-cache`는 "보관하되, 쓸 때마다 바뀌었는지 물어보라"는 뜻이라, 바뀌지 않았으면 `304`(내용 없음)로 빠르게 끝나고 바뀌었으면 새 파일을 받습니다.

## 정리

- `viewport`와 미디어 쿼리로 같은 화면을 휴대폰 폭에 맞춰 다시 배치합니다.
- 금액 칸은 `inputmode="numeric"`으로 숫자 키패드를 띄우고, 쉼표 서식을 위해 `type="text"`를 씁니다.
- `manifest.json`과 아이콘으로 홈 화면에 앱처럼 추가할 수 있고, iPhone은 `apple-touch-icon`을 씁니다.
- 정적 파일은 `no-cache`로 배포 후 예전 파일이 남지 않게 합니다.
