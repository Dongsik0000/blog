---
title: "완성된 구현 돌아보기"
slug: "fashion-archive-retrospective-review"
category: "development"
publishedAt: "2026-10-08"
summary: "코디 사진과 제품 링크를 모으는 목적을 사진 업로드, 아이템별 링크 묶음, 계정별 관리, 자체 서버 운영으로 어떻게 구현했는지 연결하고, 이 시리즈를 읽는 순서를 정리합니다."
series: "fashion-archive"
chapter: "retrospective"
seriesOrder: 2
---
## 처음 목적과 구현 연결하기

Fashion Archive는 코디 사진을 계절과 품목으로 정리하고, 사진 속 아이템과 비슷한 제품 링크를 아이템별로 모아 두려고 만든 사이트입니다. 이 글은 완성된 저장소를 기준으로, 그 목적이 어떤 구현으로 이어졌는지 돌아봅니다.

## 사진을 올리고 지키는 부분

사진 등록은 이 사이트에서 가장 많은 코드가 모인 기능입니다.

| 단계 | 구현 | 글 |
|---|---|---|
| 받기 | Servlet 3.1 multipart, 파일 10MB와 요청 12MB 제한 | [multipart](/posts/fashion-archive-upload-multipart) |
| 확인 | 파일 앞 12바이트로 jpg, png, webp 판별 | [파일 형식](/posts/fashion-archive-upload-file-type) |
| 저장 | 사용자별 폴더, UUID 이름, 644 권한 | [디스크 저장](/posts/fashion-archive-upload-local-storage) |
| 맞추기 | 파일 → DB 트랜잭션, 실패하면 파일 정리 | [파일과 DB](/posts/fashion-archive-upload-consistency) |
| 보여 주기 | nginx가 디스크에서 직접, `nosniff` | [nginx 연결](/posts/fashion-archive-deploy-nginx) |

파일을 다루는 코드는 경로, 권한, 형식에서 보안 문제가 생기기 쉽습니다. 아이디 규칙(경로 문자 금지), 삭제 경로 검사, 내용 기반 형식 판별을 각각 테스트로 고정했습니다([테스트](/posts/fashion-archive-test-tests)).

## 링크를 아이템별로 모으는 부분

DB에는 링크가 한 줄씩 있고, 서버가 `LinkedHashMap`으로 아이템 이름별로 묶어 등록 순서를 지킵니다. 상세 화면은 같은 데이터로 읽기 모드와 편집 모드를 그리고, 저장하면 서버가 묶은 결과를 다시 받아 그립니다. 같은 아이템에 링크를 연달아 넣는 흐름과 아이템 이름 제안(`datalist`)이 입력 실수를 줄입니다([링크 묶기](/posts/fashion-archive-view-link-groups), [편집 모드](/posts/fashion-archive-view-edit-mode)).

## 계정별로 나누는 부분

로그인한 사용자는 자기 사진만 봅니다. 이 규칙은 세 겹으로 지켜집니다.

1. **인터셉터**가 로그인하지 않은 요청을 막습니다.
2. **SQL**이 모든 사진 조회와 수정에 `owner_id` 조건을 겁니다.
3. **디스크**에서도 사진을 사용자별 폴더에 둡니다.

사용자 id는 항상 세션에서 꺼내고, 남의 사진과 없는 사진은 같은 응답으로 답합니다([본인 사진만](/posts/fashion-archive-database-owner-scope)).

## 표준프레임워크 위에서

eGovFrame 4.1 실행환경이 Spring MVC, MyBatis, log4j2를 검증된 버전으로 함께 가져오고, 코드는 패키지, DAO, 설정 파일 위치의 관례를 따릅니다. 같은 관례로 만든 [Ledger](/series/ledger)와 비교하면, 같은 구조가 프레임워크 버전과 레이아웃 방식이 달라도 유지되는 것을 볼 수 있습니다([Ledger와 비교](/posts/fashion-archive-egovframe-vs-ledger)).

## 자체 서버에서 운영

앱은 Tomcat 9 Docker 이미지로, 사진은 서버 디스크와 nginx로, 배포는 GitHub Actions와 배포 스크립트로 이어집니다. 배포 스크립트의 `mvn -q package`가 테스트를 함께 실행하므로, 테스트가 실패한 코드는 이미지가 만들어지지 않습니다([자동 배포](/posts/fashion-archive-deploy-deploy-actions)).

## 이 시리즈를 읽는 순서

| 목적 | 순서 |
|---|---|
| 코드 구조를 이해 | 소개 → 표준프레임워크 → Spring MVC 설정 → Tiles → DB와 MyBatis |
| 기능 하나를 따라가기 | 화면(JSON으로 그리기) → Spring MVC(컨트롤러) → DB(DAO와 매퍼) → 사진 업로드 |
| 직접 실행 | 소개의 로컬 실행 → 배포(Dockerfile, compose, nginx, 자동 배포) |
| 개발 과정 | 겪은 문제와 해결 → 설계와 완성 비교 |

구현 설명은 저장소의 실제 파일에서 확인하고, 개발 과정은 커밋에 남은 [해결 사례](/posts/fashion-archive-troubleshooting-storage-switch)와 [설계 비교](/posts/fashion-archive-retrospective-design-vs-final)로 이어서 읽을 수 있습니다.
