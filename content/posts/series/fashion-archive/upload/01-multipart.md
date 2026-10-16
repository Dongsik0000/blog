---
title: "multipart로 사진 받기와 크기 제한"
slug: "fashion-archive-upload-multipart"
category: "development"
publishedAt: "2026-10-08"
summary: "파일과 글자를 함께 보내는 multipart/form-data 요청을 브라우저의 FormData로 보내고, Servlet 3.1 multipart-config와 StandardServletMultipartResolver, @RequestParam MultipartFile로 받는 과정과 화면, Tomcat, nginx 세 곳의 크기 제한을 설명합니다."
series: "fashion-archive"
chapter: "upload"
seriesOrder: 1
---
## 이 글에서 할 일

사진 등록은 사진 파일과 제목, 메모, 카테고리를 **한 요청**으로 보냅니다. JSON은 글자만 담을 수 있어서 파일을 보낼 때는 **multipart/form-data** 형식을 씁니다. 이 글에서는 보내는 쪽(브라우저)과 받는 쪽(Tomcat, Spring)을 차례로 봅니다.

## multipart/form-data란

요청 본문을 여러 **부분**(part)으로 나누고, 부분마다 이름과 내용을 담는 형식입니다.

```text
Content-Type: multipart/form-data; boundary=----abc123

------abc123
Content-Disposition: form-data; name="title"

여름 샌들 코디
------abc123
Content-Disposition: form-data; name="categoryIds"

2
------abc123
Content-Disposition: form-data; name="categoryIds"

6
------abc123
Content-Disposition: form-data; name="file"; filename="IMG_0412.jpg"
Content-Type: image/jpeg

(사진 바이트)
------abc123--
```

`boundary`는 부분 사이의 경계 문자열입니다. 같은 이름(`categoryIds`)을 여러 번 보내 목록을 표현합니다.

## 보내기: FormData와 App.upload

`resources/js/app/photo/photoForm.js` (일부)

```js
var fd = new FormData(m$.form);
...
/* 등록은 파일이 있어 multipart, 수정은 JSON */
var request = settings.mode === 'new'
    ? App.upload(url.create, fd)
    : App.post(url.update, {
        title: fd.get('title'),
        memo: fd.get('memo'),
        categoryIds: fd.getAll('categoryIds')
    });
```

`new FormData(form)`은 `<form>` 안의 입력 칸(파일 칸 포함)을 multipart 내용으로 모읍니다. 체크한 카테고리 체크박스는 같은 이름으로 여러 개 들어갑니다(`fd.getAll('categoryIds')`).

사진 정보 **수정**에는 파일이 없으므로 JSON(`App.post`)으로 보냅니다. 같은 화면이지만 등록과 수정의 요청 형식이 다릅니다.

`resources/js/common/common.js`

```js
// App.upload(url, formData): multipart 전송. Content-Type은 브라우저가 boundary와 함께 붙이므로 지정하지 않는다.
App.upload = function (url, formData, opts) {
    opts = opts || {};
    return App._handle(fetch(url, {
        method: 'POST',
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
        credentials: 'same-origin',
        body: formData
    }), opts);
};
```

**`Content-Type`을 직접 적지 않는 것**이 핵심입니다. `body`에 `FormData`를 주면 브라우저가 `multipart/form-data; boundary=...`를 경계 문자열과 함께 만들어 붙입니다. 손으로 `multipart/form-data`만 적으면 `boundary`가 빠져 서버가 부분을 나누지 못합니다.

`X-Requested-With` 헤더는 붙입니다. 모든 `/api/` 요청은 이 헤더가 있어야 [인터셉터](/posts/fashion-archive-spring-mvc-interceptor)를 통과합니다.

## 받기 1: Tomcat이 multipart를 해석

`web.xml`

```xml
<!-- Servlet 3.1 표준 multipart. 파일 10MB, 요청 전체 12MB -->
<multipart-config>
    <max-file-size>10485760</max-file-size>
    <max-request-size>12582912</max-request-size>
</multipart-config>
```

DispatcherServlet에 `<multipart-config>`를 두면, Tomcat이 multipart 요청을 부분별로 나눠 둡니다(서블릿 표준 `request.getParts()`). 별도 업로드 라이브러리(commons-fileupload 등)가 필요 없습니다.

## 받기 2: Spring이 MultipartFile로 연결

`dispatcher-servlet.xml`

```xml
<!-- web.xml의 multipart-config를 사용. 빈 이름은 반드시 multipartResolver -->
<bean id="multipartResolver" class="org.springframework.web.multipart.support.StandardServletMultipartResolver" />
```

`StandardServletMultipartResolver`는 Tomcat이 나눠 둔 부분을 Spring의 `MultipartFile`로 감쌉니다. DispatcherServlet은 이 빈을 `multipartResolver`라는 **이름**으로 찾습니다.

## 받기 3: 컨트롤러

`PhotoApiController.create`

```java
@ResponseBody
@PostMapping("/api/admin/photos")
public Response create(@RequestParam("file") MultipartFile file,
                       @RequestParam(value = "title", required = false) String title,
                       @RequestParam(value = "memo", required = false) String memo,
                       @RequestParam(value = "categoryIds", required = false) List<Integer> categoryIds,
                       HttpSession session) {
```

| 파라미터 | 받는 것 |
|---|---|
| `@RequestParam("file") MultipartFile` | 파일 부분. `getBytes()`, `isEmpty()`, `getSize()` |
| `@RequestParam("title") String` | 글자 부분 |
| `@RequestParam("categoryIds") List<Integer>` | 같은 이름의 부분 여러 개를 숫자 목록으로 |
| `required = false` | 없으면 `null`. 비었는지는 [Validation](/posts/fashion-archive-spring-mvc-validation)이 사용자용 문장으로 검사 |

JSON API처럼 `@RequestBody`가 아니라 `@RequestParam`으로 받습니다. multipart의 부분은 폼 파라미터처럼 다뤄지기 때문입니다.

`@RequestParam("file")`처럼 **이름을 적어 둡니다.** 이름을 생략하면 Spring이 Java 파라미터 이름을 써야 하는데, 컴파일 옵션(`-parameters`)에 따라 그 이름을 알 수 없을 수 있습니다.

## 크기 제한은 세 곳

| 위치 | 설정 | 넘으면 |
|---|---|---|
| 화면 JS | `settings.maxBytes` (10MB) | 보내기 전에 "10MB 이하의 사진만 올릴 수 있어요" |
| nginx | `client_max_body_size 12m` | 413 응답 (Tomcat까지 가지 않음) |
| Tomcat | `max-file-size` 10MB, `max-request-size` 12MB | 업로드 예외 |

```js
if(settings.mode === 'new' && fd.get('file').size > settings.maxBytes){
    App.error('알림', '10MB 이하의 사진만 올릴 수 있어요.');
    return;
}
```

- **화면 JS**는 사용자에게 빨리 알려 주기 위한 것입니다. 10MB 사진을 다 올린 뒤에 거절당하지 않게 합니다.
- **nginx**는 서버 앞에서 너무 큰 요청을 끊어 Tomcat을 보호합니다.
- **Tomcat**이 최종 기준입니다. 화면 JS 검사는 우회할 수 있으므로 서버에도 같은 제한이 있어야 합니다.

요청 전체(12MB)를 파일 하나(10MB)보다 크게 잡은 것은 제목, 메모, 카테고리, multipart 경계 문자열이 차지하는 여유입니다.

## 정리

- 파일과 글자를 함께 보낼 때는 `multipart/form-data`를 쓰고, 브라우저의 `FormData`로 만듭니다.
- `fetch`에 `FormData`를 줄 때 `Content-Type`은 브라우저가 `boundary`와 함께 붙이도록 비워 둡니다.
- Tomcat이 `<multipart-config>`로 해석하고, `StandardServletMultipartResolver`가 `MultipartFile`로 연결합니다.
- 크기는 화면 JS(안내), nginx(보호), Tomcat(최종) 세 곳에서 제한합니다.
