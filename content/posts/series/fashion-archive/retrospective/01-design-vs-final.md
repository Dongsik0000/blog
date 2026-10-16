---
title: "설계 문서와 완성된 구현 비교"
slug: "fashion-archive-retrospective-design-vs-final"
category: "development"
publishedAt: "2026-10-08"
summary: "2026-09-16의 설계 문서와 완성된 저장소를 항목별로 비교해, 관리자 한 명의 공개 사이트가 계정별 사진 관리로, 외부 저장소가 서버 디스크로, 호스팅 후보가 자체 서버 배포로 바뀐 과정을 커밋과 함께 정리합니다."
series: "fashion-archive"
chapter: "retrospective"
seriesOrder: 1
---
## 이 글에서 할 일

Fashion Archive는 코드를 쓰기 전에 설계 문서(`docs/superpowers/specs/2026-09-16-fashion-archive-design.md`)와 구현 계획을 먼저 만들었습니다. 이 글에서는 그 설계와 완성된 저장소를 나란히 놓고, 무엇이 그대로이고 무엇이 어떤 커밋에서 바뀌었는지 정리합니다.

## 그대로 유지된 것

| 항목 | 설계 문서 | 완성된 구현 |
|---|---|---|
| 프레임워크 | eGovFrame 4.1, Spring 5.3.20, `javax.servlet`, Java 17 실행 | 같음 |
| 설정 방식 | `web.xml`, `dispatcher-servlet.xml`, `context-datasource.xml` + `globals.properties` | 같음 |
| 화면 | JSP + Tiles 3, 로그인 화면은 레이아웃 없음 | 같음 |
| 사진과 카테고리 | 사진 1장이 여러 카테고리에 속함 (다대다) | `photo_category` 연결 테이블 |
| 제품 링크 | `item_label`로 묶음 | `groupLinks`로 묶어 응답 |
| 로그인 | 세션에 `loginId`, `userId` 저장, BCrypt, 판정은 컨트롤러 | 같음 |
| 세션 | 60분, 쿠키 전용 | `web.xml` 그대로 |
| 응답 | `Response.of(code, message, data)`, 성공 `00`, 실패 `99`, 로그인 실패 `01` | 같음 |
| 실행 | `tomcat:9-jdk17` Docker 이미지 + war | 같음 |

틀(프레임워크, 설정 파일 구성, 응답 형식)은 설계대로 끝까지 갔습니다. 바뀐 것은 **누가 쓰는가**와 **어디에 두는가**입니다.

## 바뀐 것

| 항목 | 설계 문서 | 완성된 구현 | 바뀐 커밋 |
|---|---|---|---|
| 쓰는 사람 | 관리자 1명이 등록, 누구나 봄 | 가입 코드로 가입한 사용자가 자기 사진만 보고 고침 | `38f6f95` (09-29) |
| 공개 범위 | 목록과 상세는 공개 | 로그인과 가입 화면 말고 모두 로그인 필요 | `38f6f95` |
| 사진 저장 | Supabase Storage (REST 업로드) | 서버 디스크의 사용자별 폴더 + nginx | `e9b57d5` (09-21), `38f6f95` |
| 회원가입 | 범위 밖 | 가입 코드 회원가입, 같은 IP 5회 실패 5분 차단 | `38f6f95` |
| 화면 데이터 | 화면 컨트롤러와 API 분리 | 화면 컨트롤러는 라우팅만, 목록과 상세는 JSON API로 그림 | `66fd242` (09-16) |
| 프런트 라이브러리 | jQuery + `App.post()` | jQuery 없이 `fetch` 기반 `App.ajax`, `App.upload` | |
| 배포 | 호스팅 서비스 후보 중 배포 시점에 선택 | 자체 서버의 Docker + nginx `/p1/`, GitHub Actions 자동 배포 | `e9b57d5`, `f41f09c` (09-30) |

### 1. 관리자 한 명의 사이트에서 계정별 관리로

설계는 관리자 한 명을 전제로 했지만, 테이블에는 처음부터 `photo.owner_id`를 두었습니다. 설계 문서의 결정 표에 "회원 확장 대비로 `photo.owner_id`만 미리 둔다"고 적혀 있습니다.

그래서 계정별 관리로 바꿀 때(커밋 `38f6f95`) **테이블 구조를 바꾸지 않고** SQL 조건만 더했습니다.

- 사진 조회, 수정, 삭제 SQL에 `owner_id = #{ownerId}` 조건 추가
- 링크 수정, 삭제는 사진의 주인으로 확인하는 하위 쿼리
- 인터셉터 매핑을 `/admin/**`에서 `/**`로 넓혀 모든 화면을 로그인 대상으로

자세한 내용은 [본인 사진만](/posts/fashion-archive-database-owner-scope)에 있습니다.

### 2. 로그인 시도 제한

설계 문서 8절은 로그인 실패 잠금을 넣지 않기로 했습니다.

> 로그인 실패 잠금은 넣지 않는다. 관리자 1명이라 잠기면 DB를 직접 고쳐야 풀린다. BCrypt 자체가 느린 해시라 무차별 대입 비용이 크다.

완성된 구현에서도 **로그인 API에는 시도 제한이 없습니다.** 대신 회원가입이 생기면서 **가입 코드**를 무작위로 맞혀 보는 시도를 막는 제한(같은 IP 5회 실패 5분 차단)이 가입 API에 들어갔습니다. 막아야 할 대상이 새로 생긴 곳에 제한을 둔 것입니다([가입](/posts/fashion-archive-auth-signup)).

### 3. 저장 위치

사진 저장은 두 번 바뀌었습니다.

```text
설계:     Supabase Storage
09-21:    서버 디스크 /srv/fashion-uploads/{파일}          (e9b57d5)
09-29:    서버 디스크 /srv/fashion-uploads/{아이디}/{파일}  (38f6f95)
```

DB에는 처음부터 파일이 아니라 공개 주소(`image_url`)를 저장했기 때문에, 두 번 모두 테이블은 그대로였습니다. 저장 클래스도 `upload`, `delete` 두 메서드의 모양을 유지해 컨트롤러 변경이 작았습니다([저장소 전환](/posts/fashion-archive-troubleshooting-storage-switch)).

## 설계 문서를 먼저 쓴 효과

- 프레임워크, 설정 구조, 응답 형식처럼 바꾸기 어려운 것은 처음 정한 대로 끝까지 갔습니다.
- 바뀐 것(사용자, 저장 위치)은 설계에서 미리 남겨 둔 자리(`owner_id`, `image_url`) 덕분에 테이블을 고치지 않고 반영했습니다.
- 넣지 않기로 한 것(로그인 잠금)과 그 이유가 문서에 남아 있어, 지금 코드에 로그인 제한이 없는 이유를 코드 밖에서도 확인할 수 있습니다.

## 정리

- 프레임워크, 설정 파일, Tiles, 응답 형식, 세션 로그인은 설계대로 구현했습니다.
- 관리자 한 명의 공개 사이트가 가입 코드 기반의 계정별 사진 관리로 바뀌었고, `owner_id` 덕분에 테이블은 그대로였습니다.
- 사진 저장은 Supabase에서 서버 디스크, 다시 사용자별 폴더로 바뀌었고, 배포는 자체 서버와 GitHub Actions로 정해졌습니다.
