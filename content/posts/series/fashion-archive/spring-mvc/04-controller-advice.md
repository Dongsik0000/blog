---
title: "@ControllerAdvice로 모든 화면에 카테고리 메뉴 넣기"
slug: "fashion-archive-spring-mvc-controller-advice"
category: "development"
publishedAt: "2026-10-08"
summary: "모든 화면의 헤더에 필요한 카테고리 목록을 @ModelAttribute 하나로 넣는 GlobalModelAdvice와, assignableTypes로 화면 컨트롤러에만 적용해 API 호출마다 조회하지 않게 한 방법을 설명합니다."
series: "fashion-archive"
chapter: "spring-mvc"
seriesOrder: 4
---
## 이 글에서 할 일

모든 화면의 헤더에는 봄, 여름, 가을, 겨울, 악세사리, 신발 메뉴가 있습니다. 메뉴는 DB의 `category` 테이블에서 옵니다. 화면 컨트롤러 메서드마다 카테고리를 조회해 `Model`에 넣으면 같은 코드가 반복됩니다. `@ControllerAdvice`로 한 곳에서 넣습니다.

## GlobalModelAdvice

`com/fashion/cmmn/controller/GlobalModelAdvice.java`

```java
// 화면 컨트롤러에만 적용해 API 호출마다 카테고리를 조회하지 않게 한다
@ControllerAdvice(assignableTypes = PhotoController.class)
public class GlobalModelAdvice {

    @Autowired
    private CmmnDAO cmmnDAO;

    @ModelAttribute("categories")
    public List<Map<String, Object>> categories() {
        return cmmnDAO.selectCategoryList();
    }
}
```

| 부분 | 뜻 |
|---|---|
| `@ControllerAdvice` | 여러 컨트롤러에 공통으로 적용할 코드를 담는 클래스 |
| `assignableTypes = PhotoController.class` | **`PhotoController`의 요청에만** 적용 |
| `@ModelAttribute("categories")` | 컨트롤러 메서드가 실행되기 전에 이 메서드를 실행하고, 결과를 `categories`라는 이름으로 `Model`에 넣음 |

`/photos/15` 요청이 오면 다음 순서로 실행됩니다.

```text
1. GlobalModelAdvice.categories()  → Model에 categories 추가
2. PhotoController.detail()        → Model에 photoId 추가, 뷰 이름 반환
3. Tiles + JSP                     → 둘 다 꺼내 씀
```

### 적용 범위를 좁힌 이유

`assignableTypes` 없이 쓰면 **모든 컨트롤러**에 적용됩니다. API 컨트롤러는 JSON만 돌려주므로 카테고리 목록이 필요 없는데도, 사진 목록 API를 부를 때마다 카테고리 조회 SQL이 실행됩니다. 주석대로 화면 컨트롤러에만 걸어 불필요한 조회를 막았습니다.

`CmmnController`(로그인, 가입 화면)도 적용 대상이 아닙니다. 로그인 화면은 레이아웃 없이 그리므로 메뉴가 없습니다.

## 레이아웃 JSP에서 쓰기

`WEB-INF/layout/fashionLayout.jsp` (일부)

```jsp
<nav class="site-nav" aria-label="카테고리">
    <c:forEach var="cat" items="${categories}">
        <a href="<c:url value='/c/${cat.slug}'/>" data-season="${cat.slug}" class="${cat.slug eq currentSlug ? 'active' : ''}"
           <c:if test="${cat.slug eq currentSlug}">aria-current="page"</c:if>><c:out value="${cat.name}"/></a>
    </c:forEach>
</nav>
```

- `categories`로 메뉴 링크를 만듭니다.
- 지금 보는 카테고리(`currentSlug`, `PhotoController.category`가 넣음)면 `active` 클래스와 `aria-current="page"`를 붙입니다.

페이지 제목도 같은 목록으로 정합니다.

```jsp
<%-- 카테고리 페이지면 "여름 - Fashion Archive". 상세 페이지 제목은 detail.js가 document.title로 바꾼다 --%>
<c:forEach var="cat" items="${categories}"><c:if test="${cat.slug eq currentSlug}"><c:set var="pageTitle" value="${cat.name}"/></c:if></c:forEach>
<title><c:out value="${empty pageTitle ? 'Fashion Archive' : pageTitle.concat(' - Fashion Archive')}"/></title>
```

## 화면 JS에도 넘기기

```jsp
<script>
    var contextPath = '${pageContext.request.contextPath}';
    // 화면 JS가 slug → 이름/색을 찾을 때 쓴다. 값은 category 테이블(관리자만 수정)
    var categories = [<c:forEach var="cat" items="${categories}" varStatus="st">{id: ${cat.id}, slug: '${cat.slug}', name: '<c:out value="${cat.name}"/>'}<c:if test="${not st.last}">, </c:if></c:forEach>];
    var isAdmin = ${not empty sessionScope.loginId};
</script>
```

같은 목록을 JS 배열로도 만들어 둡니다. 목록 카드의 계절 색 점, 상세 화면의 카테고리 이름, 등록 폼의 체크박스가 이 배열을 씁니다. API를 따로 부르지 않아도 됩니다.

## 정리

- `@ControllerAdvice` + `@ModelAttribute`로 모든 화면 요청에 카테고리 목록을 넣습니다.
- `assignableTypes`로 화면 컨트롤러에만 적용해 API 호출 때는 조회하지 않습니다.
- 레이아웃 JSP가 메뉴, 현재 위치 표시, 페이지 제목, 화면 JS용 배열을 같은 목록으로 만듭니다.
