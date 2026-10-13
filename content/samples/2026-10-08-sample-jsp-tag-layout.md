---
title: "JSP 태그 파일로 공통 레이아웃 나누기"
slug: "sample-jsp-tag-layout"
category: "development"
publishedAt: "2026-10-08"
summary: "페이지마다 반복되던 head와 header를 .tag 파일 하나로 모은 과정을 정리했습니다."
coverImage: "/resources/images/samples/dev-cover.jpg"
coverAlt: "흐린 하늘 아래 언덕을 따라 늘어선 침엽수림"
hashtags: ["JSP", "SpringMVC", "개발기록", "DevGear"]
---

## 문제: 모든 JSP에 같은 코드가 있었다

처음에는 화면마다 `<head>`, 헤더, 푸터를 복사해 넣었습니다. 메뉴 하나를 바꾸려면 **모든 JSP를 열어야** 했고, 한 곳을 빠뜨리면 화면마다 메뉴가 달라졌습니다.

## 해결: 태그 파일로 감싸기

`WEB-INF/tags/layout.tag`에 공통 뼈대를 두고, 각 화면은 본문만 넘기도록 바꿨습니다. 태그 파일 안의 `<jsp:doBody/>` 자리에 화면의 내용이 들어갑니다.

```jsp
<t:layout title="모든 글" page="posts">
    <section class="feed">...</section>
</t:layout>
```

이렇게 하면 다음이 한 곳에서 관리됩니다.

- 메타 태그와 CSS 링크
- 헤더 메뉴와 현재 페이지 표시
- 푸터

> 배운 점: 공통 부분을 나눌 때는 **바뀌는 것**(본문)과 **바뀌지 않는 것**(뼈대)을 먼저 구분하면 구조가 자연스럽게 정해집니다.

## 남은 고민

화면마다 다른 스크립트를 넣어야 할 때는 `jsp:attribute`로 조각을 넘기는 방법을 쓰고 있습니다. 조각이 많아지면 *태그 속성이 너무 늘어나지 않는지* 계속 지켜보려고 합니다.
