---
title: "요청 하나가 처리되는 흐름"
slug: "ledger-spring-request-flow"
category: "development"
publishedAt: "2026-10-08"
summary: "브라우저의 요청이 필터, DispatcherServlet, 인터셉터, Controller를 거쳐 JSP 화면이나 JSON 응답이 되기까지의 흐름을 dispatcher-servlet.xml 설정과 함께 따라갑니다."
series: "ledger"
chapter: "spring"
seriesOrder: 3
---
## 이 글에서 할 일

Ledger의 요청은 두 종류입니다.

1. **화면 요청**: 주소창에 `/p3/ledger/settings`를 입력 → HTML(JSP)
2. **Ajax 요청**: 화면의 JS가 `/p3/ledger/settings/load`를 POST → JSON

두 요청이 어떤 길을 지나는지 따라가며 `dispatcher-servlet.xml`의 설정을 하나씩 연결합니다.

## 전체 흐름

```text
브라우저
  │
  ▼
[CharacterEncodingFilter]  web.xml: UTF-8로 맞춤
  │
  ▼
[DispatcherServlet]
  │ ① 정적 자원인가? (/resources/**) ──▶ 파일 그대로 응답
  │ ② 어느 Controller 메서드인가? (HandlerMapping, @RequestMapping)
  ▼
[인터셉터 preHandle]  ApiRequestInterceptor → LoginPageInterceptor
  │   (false를 돌려주면 여기서 끝)
  ▼
[Controller 메서드]
  │
  ├─ 문자열 반환 ("settings/settingsMain") ──▶ [ViewResolver] ──▶ JSP 실행 ──▶ HTML
  └─ @ResponseBody Response 반환 ─────────────▶ [Jackson] ──▶ JSON
  │
  │ (예외가 나면) ──▶ [CmmnExceptionHandler] ──▶ error.jsp 또는 JSON
  ▼
브라우저
```

## ① 정적 자원

```xml
<mvc:resources mapping="/resources/**" location="/resources/">
    <mvc:cache-control no-cache="true"/>
</mvc:resources>
```

`/p3/resources/css/style.css` 같은 요청은 Controller를 찾지 않고 `webapp/resources/` 폴더의 파일을 바로 보냅니다. `no-cache`는 "브라우저가 저장은 하되 쓸 때마다 바뀌었는지 물어보라"는 뜻입니다. 바뀌지 않았으면 서버는 내용 없이 `304`만 보내므로 빠릅니다. 배포 후에 예전 JS가 남아 화면이 어긋나는 문제를 막습니다.

## ② Controller 찾기

```xml
<mvc:annotation-driven/>
<context:component-scan base-package="ledger"/>
```

- `component-scan`: `ledger` 패키지 아래에서 `@Controller`, `@Service`, `@Repository`, `@Component`가 붙은 클래스를 찾아 빈으로 만듭니다.
- `mvc:annotation-driven`: `@RequestMapping`, `@GetMapping`, `@PostMapping`을 읽어 주소와 메서드를 연결하고, `@RequestBody`와 `@ResponseBody`의 JSON 변환(Jackson)을 켭니다.

```java
@Controller
@RequestMapping("/ledger/settings")
public class SettingsApiController {

    @GetMapping
    public String settings() { ... }              // GET  /ledger/settings

    @ResponseBody
    @PostMapping("/load")
    public Response load(HttpSession session) { ... }   // POST /ledger/settings/load
}
```

클래스의 `@RequestMapping`과 메서드의 매핑이 합쳐져 최종 주소가 됩니다.

## ③ 인터셉터

```xml
<mvc:interceptors>
    <mvc:interceptor>
        <mvc:mapping path="/auth/**"/>
        <mvc:mapping path="/ledger/**"/>
        <bean class="ledger.interceptor.ApiRequestInterceptor"/>
    </mvc:interceptor>
    <mvc:interceptor>
        <mvc:mapping path="/ledger/**"/>
        <bean class="ledger.interceptor.LoginPageInterceptor"/>
    </mvc:interceptor>
</mvc:interceptors>
```

Controller가 실행되기 **직전에** 적힌 순서대로 검사합니다.

1. `ApiRequestInterceptor`: POST라면 Ajax이고 JSON인지 확인 (CSRF 방어)
2. `LoginPageInterceptor`: `/ledger/**`는 로그인했는지 확인

어느 하나라도 `false`를 돌려주면 Controller는 실행되지 않습니다. 자세한 코드는 [인터셉터 글](/posts/ledger-spring-interceptor)에서 봅니다.

## ④-1 화면 요청: ViewResolver와 JSP

Controller가 문자열을 돌려주면 그것은 **뷰 이름**입니다.

```java
@GetMapping
public String settings() {
    return "settings/settingsMain";
}
```

```xml
<bean class="org.springframework.web.servlet.view.BeanNameViewResolver">
    <property name="order" value="0"/>
</bean>
<bean id="viewResolver" class="org.springframework.web.servlet.view.InternalResourceViewResolver">
    <property name="prefix" value="/WEB-INF/jsp/ledger/"/>
    <property name="suffix" value=".jsp"/>
    <property name="order" value="1"/>
</bean>
```

뷰 이름을 실제 화면으로 바꾸는 **ViewResolver**가 두 개이고, `order` 순서로 시도합니다.

| 순서 | ViewResolver | 하는 일 |
|---|---|---|
| 0 | `BeanNameViewResolver` | 뷰 이름과 **같은 이름의 빈**이 있으면 그것을 씀 (예: `jsonView`) |
| 1 | `InternalResourceViewResolver` | `prefix + 이름 + suffix` 경로의 JSP를 씀 |

`"settings/settingsMain"`이라는 빈은 없으므로 1번이 `/WEB-INF/jsp/ledger/settings/settingsMain.jsp`를 찾아 실행합니다. 화면 요청은 HTML 틀만 돌려주고, 실제 데이터는 화면이 뜬 뒤 JS가 Ajax로 받아 와서 그립니다.

`redirect:`로 시작하면 다른 주소로 이동시킵니다. 로그인한 사용자가 로그인 화면에 오면 대시보드로 보내는 코드입니다.

```java
return "redirect:/ledger/dashboard";
```

## ④-2 Ajax 요청: @ResponseBody와 JSON

```java
@ResponseBody
@PostMapping("/load")
public Response load(HttpSession session) {
    ...
    return Response.of(Constants.SUCCESS, data);
}
```

`@ResponseBody`가 붙으면 반환값이 뷰 이름이 아니라 **응답 본문 그 자체**가 됩니다. Jackson이 `Response` 객체를 JSON으로 바꿉니다.

```json
{"code":"00","message":null,"data":{"payDay":25,"categories":[...]}}
```

요청 쪽의 `@RequestBody HashMap<String, Object> param`은 반대로 요청 본문의 JSON을 Map으로 바꿔 줍니다.

### jsonView는 언제 쓰나

설정에는 `jsonView`라는 빈도 있습니다.

```xml
<bean id="jsonView" class="org.springframework.web.servlet.view.json.MappingJackson2JsonView">
    <property name="contentType" value="application/json;charset=UTF-8"/>
</bean>
```

`ModelAndView("jsonView")`를 돌려주면 0순위 `BeanNameViewResolver`가 이 빈을 찾아 모델을 JSON으로 씁니다. 전자정부 프레임워크 프로젝트에서 흔히 쓰는 방식입니다. Ledger의 Controller는 `@ResponseBody`로 통일했고, `jsonView`는 [공통 예외 처리](/posts/ledger-spring-exception-handling)에서 Ajax 오류 응답을 만들 때만 씁니다.

## 정리

- 정적 자원은 `mvc:resources`가 바로 응답하고, 나머지는 `@RequestMapping`으로 Controller 메서드를 찾습니다.
- Controller 전에 인터셉터가 CSRF와 로그인을 검사합니다.
- 문자열을 돌려주면 ViewResolver가 JSP를 찾고, `@ResponseBody`면 Jackson이 JSON으로 바꿉니다.
- 예외는 `CmmnExceptionHandler`가 한곳에서 처리합니다.
