---
title: "화면 컨트롤러와 API 컨트롤러 나누기"
slug: "fashion-archive-spring-mvc-controllers"
category: "development"
publishedAt: "2026-10-08"
summary: "주소를 받아 뷰 이름만 돌려주는 PhotoController와 JSON을 주고받는 PhotoApiController를 나눈 구조, @PathVariable과 Model로 화면에 id를 넘기는 방법, API 주소 규칙과 Response 응답을 설명합니다."
series: "fashion-archive"
chapter: "spring-mvc"
seriesOrder: 3
---
## 이 글에서 할 일

Fashion Archive는 컨트롤러를 **화면용**과 **API용** 클래스로 나눕니다. 화면 컨트롤러는 화면 틀만 그리고, 데이터는 화면이 뜬 뒤 JS가 API로 받아 옵니다(커밋 `66fd242` "list/detail pages render from JSON api (page controllers route only)").

| 클래스 | 하는 일 | 반환 |
|---|---|---|
| `PhotoController` | 주소를 받아 화면을 고름 | 뷰 이름 (Tiles → JSP) |
| `PhotoApiController` | 조회, 등록, 수정, 삭제 | `Response` (JSON) |
| `CmmnController` | 로그인, 가입 화면, 로그아웃 | 뷰 이름, redirect |
| `CmmnApiController` | 로그인, 가입 처리 | `Response` (JSON) |

## 화면 컨트롤러

`com/fashion/photo/controller/PhotoController.java`

```java
@Controller
public class PhotoController {

    @GetMapping("/")
    public String home() {
        return "/fashion/photo/list";
    }

    @GetMapping("/c/{slug}")
    public String category(@PathVariable String slug, Model model) {
        model.addAttribute("currentSlug", slug);
        return "/fashion/photo/list";
    }

    @GetMapping("/photos/{id}")
    public String detail(@PathVariable long id, Model model) {
        model.addAttribute("photoId", id);
        return "/fashion/photo/detail";
    }

    @GetMapping("/admin/photos/new")
    public String photoNew() {
        return "/fashion/admin/photoForm";
    }

    @GetMapping("/admin/photos/{id}/edit")
    public String photoEdit(@PathVariable long id, Model model) {
        model.addAttribute("photoId", id);
        return "/fashion/admin/photoForm";
    }
}
```

DB를 부르지 않습니다. 하는 일은 두 가지뿐입니다.

1. **어느 화면인지** 정해 뷰 이름을 돌려줍니다.
2. 주소에 있는 값(`slug`, `id`)을 `Model`에 담아 JSP에 넘깁니다.

### @PathVariable

```java
@GetMapping("/photos/{id}")
public String detail(@PathVariable long id, Model model)
```

주소의 `{id}` 자리 값을 파라미터로 받습니다. `/photos/15`면 `id`는 15입니다. 타입이 `long`이라 숫자가 아니면(`/photos/abc`) Spring이 400 오류로 거절합니다.

### Model에서 JSP로, JSP에서 JS로

```jsp
<article class="photo-detail" id="photoDetail" data-id="${photoId}" data-editing="false" hidden>
```

JSP는 받은 `photoId`를 `data-id` 속성에 적어 두고, 화면 JS가 이 값을 읽어 API를 부릅니다. 같은 JSP(`photoForm.jsp`)를 등록과 수정에 함께 쓰고, 수정일 때만 `photoId`가 있습니다.

## API 컨트롤러

`com/fashion/photo/controller/PhotoApiController.java`

| 주소 | 입력 | 하는 일 |
|---|---|---|
| `POST /api/photos/list` | JSON `{slug}` | 내 사진 목록 (slug가 없으면 전체) |
| `POST /api/photos/detail` | JSON `{id}` | 사진, 카테고리 id, 아이템별 링크 |
| `POST /api/admin/photos` | multipart: `file`, `title`, `memo`, `categoryIds` | 사진 등록 |
| `POST /api/admin/photos/{id}` | JSON `{title, memo, categoryIds}` | 사진 정보 수정 |
| `POST /api/admin/photos/{id}/delete` | | 사진 삭제 |
| `POST /api/admin/photos/{photoId}/links` | JSON `{itemLabel, url, title, note}` | 링크 추가 |
| `POST /api/admin/links/{linkId}` | 같음 | 링크 수정 |
| `POST /api/admin/links/{linkId}/delete` | | 링크 삭제 |

규칙이 보입니다.

- **모든 API가 `/api/`로 시작합니다.** [인터셉터](/posts/fashion-archive-spring-mvc-interceptor)가 이 접두사로 API 요청을 알아보고 Ajax 헤더를 검사합니다.
- **데이터를 바꾸는 API는 `/api/admin/` 아래**에 있습니다.
- **모두 POST**입니다. 삭제도 `/delete`를 붙인 POST로 만듭니다.

### 메서드 하나의 모양

```java
@ResponseBody
@PostMapping("/api/photos/detail")
public Response detail(@RequestBody HashMap<String, Object> param, HttpSession session) {
    try {
        long id = Long.parseLong(String.valueOf(param.get("id")));
        Map<String, Object> photo = photoService.selectPhoto(id, ownerId(session));
        if (photo == null) {
            return Response.of(Constants.FAIL, "사진을 찾을 수 없습니다.", null);
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("photo", photo);
        data.put("categoryIds", photoService.selectPhotoCategoryIds(id));
        data.put("linkGroups", groupLinks(photoService.selectLinkList(id)));
        return Response.of(Constants.SUCCESS, data);
    } catch (NumberFormatException e) {
        return Response.of(Constants.FAIL, "잘못된 요청입니다.", null);
    } catch (Exception e) {
        logger.error("사진 상세 조회 중 오류", e);
        return Response.of(Constants.FAIL, "사진을 불러오지 못했습니다.", null);
    }
}
```

| 부분 | 뜻 |
|---|---|
| `@RequestBody HashMap` | 요청 JSON을 Map으로 |
| `ownerId(session)` | 로그인한 사용자 id. 요청 본문이 아니라 세션에서 꺼냄 |
| `photo == null` | 없는 사진이거나 다른 사람의 사진. 둘을 같은 응답으로 |
| `Response.of(...)` | `{code, message, data}` JSON |
| `catch` | 숫자가 아닌 id는 "잘못된 요청", 그 밖의 오류는 로그를 남기고 일반 문장 |

한 번의 요청으로 상세 화면에 필요한 것(사진, 카테고리, 링크 묶음)을 모두 담아 보냅니다. 화면 JS는 이 응답 하나로 화면 전체를 그립니다.

## 나눈 구조의 결과

- 화면 주소(`/photos/15`)와 데이터 주소(`/api/photos/detail`)가 분리되어, 인터셉터가 `/api/` 접두사 하나로 두 종류를 구분합니다.
- 화면 컨트롤러는 DB를 부르지 않으므로, 화면 공통 데이터(카테고리 메뉴)만 [`@ControllerAdvice`](/posts/fashion-archive-spring-mvc-controller-advice)로 붙이면 됩니다.
- 저장 후에는 JS가 해당 영역만 다시 그리므로 페이지 전체를 새로 고치지 않습니다.

## 정리

- `PhotoController`는 뷰 이름과 주소 값만 넘기고, `PhotoApiController`가 JSON으로 데이터를 주고받습니다.
- `@PathVariable`로 주소의 값을 받아 `Model` → JSP `data-` 속성 → 화면 JS로 넘깁니다.
- API는 `/api/`로 시작하고, 데이터를 바꾸는 것은 `/api/admin/` 아래의 POST입니다.
- 사용자 id는 세션에서 꺼내고, 남의 사진은 없는 사진과 같은 응답을 줍니다.
