---
title: "태그 파일로 공통 레이아웃 만들기"
slug: "ledger-view-layout-tag"
category: "development"
publishedAt: "2026-10-08"
summary: "모든 화면이 공유하는 머리말, 메뉴, 꼬리말을 JSP 태그 파일(layout.tag) 하나로 만들고, 편집 창을 xxxEditor.tag로 조각내 재사용하는 방법을 설명합니다."
series: "ledger"
chapter: "view"
seriesOrder: 2
---
## 이 글에서 할 일

화면 10개가 모두 같은 메뉴, 같은 CSS, 같은 공통 JS를 씁니다. 화면마다 복사하면 메뉴 하나를 고칠 때 10곳을 고쳐야 합니다. Ledger는 **JSP 태그 파일**로 공통 틀을 한 곳에 둡니다. 별도 라이브러리(Tiles 등) 없이 JSP 표준 기능만 씁니다.

## 태그 파일이란

`WEB-INF/tags/` 폴더의 `.tag` 파일은 **직접 만든 JSP 태그**가 됩니다. `taglib.jsp`에서 이 폴더를 `t`라는 이름으로 등록했으므로, `layout.tag`는 `<t:layout>`으로 씁니다.

```jsp
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>
```

## layout.tag: 공통 틀

`WEB-INF/tags/layout.tag` (주요 부분)

```jsp
<%@ tag language="java" pageEncoding="UTF-8" body-content="scriptless"%>
<%@ attribute name="title" required="false"%>
<%@ attribute name="page" required="false"%>
<%@ attribute name="script" required="false" fragment="true"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<c:set var="uiPage" value="${page}" scope="request"/>
<!DOCTYPE html>
<html lang="ko">
<head>
    <title><c:if test="${not empty title}"><c:out value="${title}"/> · </c:if>나의 가계부</title>
    <link rel="stylesheet" href="<c:url value='/resources/css/style.css'/>">
    <script>var contextPath = "${pageContext.request.contextPath}";</script>
    <script src="<c:url value='/resources/js/common/common.js'/>"></script>
    <script src="<c:url value='/resources/js/common/modal.js'/>"></script>
    <jsp:invoke fragment="script"/>
</head>
<body>
<a class="skip-link" href="#main-content">본문으로 바로가기</a>
<div class="app-shell">
    <jsp:include page="/WEB-INF/layout/ledgerMenu.jsp"/>
    <main class="app-main" id="main-content" tabindex="-1">
        ...
        <div class="page-content">
            <jsp:doBody/>
            <footer class="subtle-footer">...</footer>
        </div>
    </main>
</div>
</body>
</html>
```

| 부분 | 뜻 |
|---|---|
| `attribute name="title"` | 태그를 쓸 때 넘기는 값. `<t:layout title="설정">` |
| `attribute name="page"` | 메뉴에서 현재 화면을 표시하는 데 씀 |
| `attribute name="script" fragment="true"` | **HTML 조각**을 넘겨받는 칸. 화면별 `<script>` 태그를 넣음 |
| `jsp:invoke fragment="script"` | 넘겨받은 조각을 `<head>`의 이 자리에 출력 |
| `jsp:doBody` | 태그 안에 쓴 본문을 이 자리에 출력 |
| `c:set ... scope="request"` | `page` 값을 요청 범위에 저장해, include한 메뉴 JSP도 읽게 함 |

## 화면 JSP에서 쓰기

`WEB-INF/jsp/ledger/settings/settingsMain.jsp`

```jsp
<t:layout title="설정" page="settings">
    <jsp:attribute name="script">
        <script defer src="<c:url value='/resources/js/app/settings/settings.js'/>"></script>
        <script defer src="<c:url value='/resources/js/app/settings/holiday.js'/>"></script>
    </jsp:attribute>
    <jsp:body>
        <header class="page-head">
            <h1>설정</h1>
            ...
        </header>
        ...
    </jsp:body>
</t:layout>
```

화면 JSP에는 **그 화면만의 내용**만 있습니다. 머리말, 메뉴, 꼬리말, 공통 JS는 모두 레이아웃이 붙입니다. 공통 JS(`common.js`)는 `defer` 없이 먼저 읽히고, 화면 JS는 `defer`로 HTML을 다 읽은 뒤 실행되므로 화면 JS에서 `App.ajax` 같은 공통 함수를 바로 쓸 수 있습니다.

## 메뉴의 현재 위치 표시

`WEB-INF/layout/ledgerMenu.jsp` (일부)

```jsp
<a class="nav-item${requestScope.uiPage eq 'settings' ? ' is-active' : ''}"
   aria-current="${requestScope.uiPage eq 'settings' ? 'page' : 'false'}"
   href="<c:url value='/ledger/settings'/>">
```

레이아웃이 요청 범위에 넣은 `uiPage`를 보고 현재 메뉴에 `is-active` 클래스(눈에 보이는 표시)와 `aria-current="page"`(화면 낭독기용 표시)를 붙입니다.

## 편집 창 조각: xxxEditor.tag

거래 추가 창은 대시보드와 거래 내역 두 화면에서 씁니다. 이런 조각도 태그 파일로 만듭니다.

`WEB-INF/tags/entryEditor.tag` (앞부분)

```jsp
<%@ tag language="java" pageEncoding="UTF-8" body-content="empty"%>
<%@ attribute name="id" required="true"%>
<%-- 거래 추가·수정 창(화면에 하나). 값은 entry.js 가 채우고 showModal() 로 연다 --%>
<dialog class="editor-dialog" id="${id}" aria-labelledby="${id}-heading">
    <header class="dialog-head">
        <h2 id="${id}-heading">거래 추가</h2>
        <button type="button" class="icon-button" data-close aria-label="닫기">×</button>
    </header>
    <div class="dialog-body">
        <label class="field">
            <span>금액 (원)</span>
            <input type="text" id="${id}-amount" inputmode="numeric" autocomplete="off" placeholder="0" class="amount-input">
        </label>
        ...
```

- `id` 속성을 받아 안의 모든 요소 id 앞에 붙입니다(`${id}-amount`). 한 화면에 같은 조각을 두 번 써도 id가 겹치지 않습니다.
- `body-content="empty"`: 이 태그는 본문 없이 `<t:entryEditor id="entryEditor"/>`처럼 씁니다.
- 브라우저 기본 `<dialog>` 요소를 씁니다. `showModal()`로 열면 뒤 화면을 막고, 포커스를 창 안에 가두고, Esc로 닫히는 동작을 브라우저가 해 줍니다.

Ledger의 편집 창 태그는 거래, 고정 항목, 정기 이체, 이체, 자산, 통장 6개입니다.

![거래 수정 창. 금액 4,800원, 지출, 카페/간식, 내용 오후 커피, 신용카드가 선택되어 있다](/uploads/capture-ledger-08-entry-editor.jpg)

## Tiles와 비교

| | Tiles (fashion-archive) | 태그 파일 (Ledger) |
|---|---|---|
| 의존성 | Tiles 라이브러리 | 없음 (JSP 표준) |
| 레이아웃 정의 | XML 파일에 화면별로 등록 | 각 JSP가 `<t:layout>`으로 자기를 감쌈 |
| Controller 반환값 | Tiles 정의 이름 | JSP 경로 |
| 화면별 JS 넣기 | 속성 정의 | `fragment` 속성 |

## 정리

- `WEB-INF/tags/*.tag`는 직접 만든 JSP 태그가 됩니다.
- `layout.tag`가 머리말, 메뉴, 꼬리말, 공통 JS를 담고, 화면 JSP는 `<t:layout>`으로 본문만 넘깁니다.
- 화면별 JS는 `fragment` 속성으로, 본문은 `jsp:doBody`로 받습니다.
- 여러 화면이 쓰는 편집 창은 `xxxEditor.tag`로 만들고, `id` 속성으로 요소 id가 겹치지 않게 합니다.
