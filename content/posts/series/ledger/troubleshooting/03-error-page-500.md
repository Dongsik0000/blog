---
title: "오류 JSP가 404를 500으로 바꾸던 문제"
slug: "ledger-troubleshooting-error-page-500"
category: "development"
publishedAt: "2026-10-08"
summary: "오류 페이지의 isErrorPage 설정으로 상태 코드가 바뀌었던 커밋 기록과, 현재 JSP에서 해당 설정을 제거한 해결을 설명합니다."
series: "ledger"
chapter: "troubleshooting"
seriesOrder: 3
---
## 해결한 문제

404나 400 응답을 오류 화면에 표시하는 과정에서 HTTP 상태가 500으로 바뀌던 문제가 있었습니다. `ea57167` 커밋은 Spring이 남긴 예외 속성과 JSP의 `isErrorPage=true` 설정이 관련되어 있었다고 기록합니다. 현재 오류 JSP에서는 이 설정을 제거했습니다.

## 오류 화면과 상태 코드는 함께 유지

404는 요청한 페이지를 찾지 못했다는 뜻이고, 500은 서버 내부 오류라는 뜻입니다. 같은 안내 화면을 보여 주더라도 요청의 원래 상태를 유지해야 브라우저와 운영 로그가 결과를 올바르게 해석합니다.

Ledger의 `web.xml`은 404, 500과 그 밖의 오류를 같은 `/WEB-INF/jsp/ledger/error.jsp`로 연결합니다. 이 연결과 JSP의 화면 표시를 함께 확인합니다.

## 현재 JSP의 선언

`src/main/webapp/WEB-INF/jsp/ledger/error.jsp` (파일 시작 부분)

```jsp
<%-- isErrorPage 를 쓰지 않는다: 켜면 Spring 이 남긴 예외 속성 때문에 JSP 가 상태를 500 으로 덮어써 404/400 이 500 이 된다 --%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
```

파일의 주석은 설정을 제거한 이유를 남기고 있습니다. 이 문장은 당시 프로젝트에서 확인하고 수정한 사례이며, 모든 오류 JSP에 대한 일반 규칙으로 확대하지 않습니다.

## 확인 방법

개발 환경에서 존재하지 않는 페이지를 요청합니다. 로그인 검사와 혼동하지 않도록 먼저 로그인하고, 없는 경로를 여는 것이 좋습니다.

브라우저 개발자 도구의 Network에서 실제 HTTP 상태가 404인지 보고, 화면에는 공통 오류 안내가 나오는지 확인합니다. 화면이 보인다는 사실만으로 상태 코드까지 맞는다고 판단하지 않습니다.

기록은 [해결 커밋](https://github.com/Dongsik0000/ledger/commit/ea57167281b4e004a4dc8a08b1c9d2b3504df733)과 현재 `error.jsp`, `web.xml`을 근거로 작성했습니다.
