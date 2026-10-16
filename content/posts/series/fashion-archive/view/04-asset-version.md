---
title: "배포 후 예전 CSS와 JS를 막는 정적 파일 버전"
slug: "fashion-archive-view-asset-version"
category: "development"
publishedAt: "2026-10-08"
summary: "앱이 시작될 때 시각을 기록하는 AssetVersionListener와, JSP가 CSS와 JS 주소 뒤에 ?v=시각을 붙여 배포할 때마다 브라우저가 새 파일을 받게 하는 캐시 무효화 방법을 설명합니다."
series: "fashion-archive"
chapter: "view"
seriesOrder: 4
---
## 이 글에서 할 일

브라우저는 한 번 받은 CSS와 JS를 저장해 두고(캐시) 다시 씁니다. 그런데 새 버전을 배포한 뒤에도 브라우저가 예전 `detail.js`를 쓰면, 서버의 새 API 응답과 맞지 않아 화면이 깨질 수 있습니다. Fashion Archive는 **주소에 버전을 붙여** 배포할 때마다 새 파일을 받게 합니다(커밋 `43824d6`의 "asset cache-busting").

## 원리: 주소가 바뀌면 새 파일

브라우저 캐시는 **주소** 단위입니다.

```text
배포 1:  /p1/resources/js/app/photo/detail.js?v=1790000000000
배포 2:  /p1/resources/js/app/photo/detail.js?v=1790500000000   ← 다른 주소 → 새로 받음
```

`?v=` 뒤의 값은 서버가 무시하는 꼬리표입니다. 파일은 같은 파일이지만, 브라우저 입장에서는 처음 보는 주소라 캐시를 쓰지 않습니다.

## 1. 시작 시각 기록: AssetVersionListener

`com/fashion/cmmn/listener/AssetVersionListener.java`

```java
// 정적 파일 URL에 붙일 버전(기동 시각). 배포 후 브라우저가 예전 css/js를 캐시에서 쓰는 것을 막는다
public class AssetVersionListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().setAttribute("assetVersion", String.valueOf(System.currentTimeMillis()));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
```

`web.xml`

```xml
<listener>
    <listener-class>com.fashion.cmmn.listener.AssetVersionListener</listener-class>
</listener>
```

| 부분 | 뜻 |
|---|---|
| `ServletContextListener` | 웹 앱이 시작되고 끝날 때 불리는 서블릿 표준 인터페이스 |
| `contextInitialized` | 시작할 때 한 번 실행 |
| `setAttribute("assetVersion", ...)` | **애플리케이션 범위**에 값을 저장. 모든 요청, 모든 JSP가 읽을 수 있음 |
| `System.currentTimeMillis()` | 지금 시각(밀리초) |

배포하면 컨테이너가 새로 만들어지고 앱이 다시 시작되므로, 시각이 바뀌어 버전도 바뀝니다. 버전 번호를 손으로 올릴 필요가 없습니다.

Spring 빈이 아니라 서블릿 리스너인 이유는, 이 값이 Spring과 상관없는 **JSP 화면 전체**에 필요하기 때문입니다. 레이아웃 없는 로그인 화면도 같은 값을 씁니다.

## 2. JSP에서 붙이기

`WEB-INF/layout/fashionLayout.jsp`

```jsp
<link rel="stylesheet" href="<c:url value='/resources/css/reset.css'/>?v=${applicationScope.assetVersion}">
<link rel="stylesheet" href="<c:url value='/resources/css/fashion.css'/>?v=${applicationScope.assetVersion}">
<script src="<c:url value='/resources/js/common/common.js'/>?v=${applicationScope.assetVersion}"></script>
```

`WEB-INF/jsp/fashion/photo/detail.jsp`

```jsp
<script defer src="<c:url value='/resources/js/app/photo/detail.js'/>?v=${applicationScope.assetVersion}"></script>
```

`${applicationScope.assetVersion}`은 리스너가 저장한 값을 EL로 꺼냅니다. 모든 CSS와 JS 주소에 같은 꼬리표를 붙입니다.

## 같은 서버의 Ledger와 비교

같은 문제를 [Ledger](/posts/ledger-view-mobile)는 다른 방법으로 풉니다.

| | Fashion Archive | Ledger |
|---|---|---|
| 방법 | 주소에 버전(`?v=기동 시각`) | 응답 헤더 `Cache-Control: no-cache` |
| 브라우저 동작 | 버전이 같으면 캐시를 그대로 씀. 바뀌면 새로 받음 | 쓸 때마다 서버에 바뀌었는지 물어봄(안 바뀌었으면 304) |
| 설정 위치 | 리스너 + JSP | `dispatcher-servlet.xml`의 `mvc:resources` |

## 정리

- 브라우저 캐시는 주소 단위라, 주소에 버전을 붙이면 배포 후 새 파일을 받습니다.
- `AssetVersionListener`가 앱 시작 시각을 애플리케이션 범위에 저장합니다.
- JSP는 모든 CSS와 JS 주소에 `?v=${applicationScope.assetVersion}`을 붙입니다.
- 배포하면 앱이 다시 시작되어 버전이 자동으로 바뀝니다.
