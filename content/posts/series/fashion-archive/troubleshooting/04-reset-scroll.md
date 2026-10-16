---
title: "reset.css가 페이지 스크롤을 막던 문제"
slug: "fashion-archive-troubleshooting-reset-scroll"
category: "development"
publishedAt: "2026-10-08"
summary: "html과 body에 height 100%와 overflow hidden을 주던 reset.css 규칙 때문에 긴 페이지가 스크롤되지 않던 문제와, 그 네 줄을 지워 브라우저 기본 스크롤로 되돌린 해결을 설명합니다."
series: "fashion-archive"
chapter: "troubleshooting"
seriesOrder: 4
---
## 해결한 문제

사진이 많은 목록이나 링크가 많은 상세 화면에서 **페이지가 아래로 스크롤되지 않는** 문제가 있었습니다. 2026-09-16의 커밋 `85f1783`("fix page scroll locked by reset.css")이 이 문제를 고쳤습니다.

## 원인

`reset.css`는 브라우저마다 다른 기본 스타일을 지우는 공통 CSS입니다. 이 파일의 `html`, `body` 규칙에 다음 네 줄이 있었습니다.

```css
html,
body {
	width: 100%;
	height: 100%;
	overflow-x: hidden;
	overflow-y: hidden;
	position: relative;
}
```

| 줄 | 결과 |
|---|---|
| `height: 100%` | `html`과 `body`의 높이가 **화면 높이로 고정** |
| `overflow-y: hidden` | 그 높이를 넘는 내용은 **잘리고 스크롤바가 생기지 않음** |

두 줄이 함께 있으면, 내용이 화면보다 길어도 문서 전체가 스크롤되지 않습니다. Fashion Archive의 목록과 상세 화면은 문서 전체가 아래로 길어지는 구조라 이 규칙과 맞지 않았습니다.

## 해결

```diff
 html,
 body {
 	width: 100%;
-	height: 100%;
-	overflow-x: hidden;
-	overflow-y: hidden;
-	position: relative;
 }
```

네 줄을 지워 높이와 스크롤을 **브라우저 기본 동작**으로 되돌렸습니다. 문서가 내용만큼 길어지고 브라우저가 스크롤을 처리합니다. 새 규칙을 더하지 않고 막던 규칙을 지우는 것이 가장 작은 수정이었습니다.

## 원인을 찾는 곳

스크롤이 안 되는 원인이 화면별 CSS(`fashion.css`)가 아니라 모든 화면이 먼저 읽는 공통 파일(`reset.css`)에 있었습니다. 스크롤, 높이, `overflow`가 이상하면 개발자 도구의 Elements 패널에서 `html`과 `body`를 골라, 적용된 규칙과 그 규칙이 들어 있는 파일부터 확인합니다.

## 확인 방법

1. 사진을 화면 높이보다 많이 올린 목록 화면을 엽니다.
2. 마우스 휠과 휴대폰 손가락으로 끝까지 스크롤되는지 확인합니다.
3. 개발자 도구에서 `html`, `body`의 계산된 `overflow-y`가 `visible`(기본값)인지 확인합니다.

기록은 [해결 커밋](https://github.com/Dongsik0000/fashion-archive/commit/85f17835def970f6b0bb1eadac35dfe530cfd10f)과 현재 `reset.css`를 근거로 작성했습니다.
