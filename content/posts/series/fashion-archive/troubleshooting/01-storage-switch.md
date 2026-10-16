---
title: "사진 저장소를 Supabase에서 서버 디스크로 옮기기"
slug: "fashion-archive-troubleshooting-storage-switch"
category: "development"
publishedAt: "2026-10-08"
summary: "처음 설계의 Supabase Storage 업로드를 서버 로컬 디스크 저장과 nginx 정적 응답으로 바꾼 커밋을 따라가며, 같은 메서드 이름 덕분에 컨트롤러가 4줄만 바뀐 구조와 설정, 배포 파일이 함께 바뀐 내용을 정리합니다."
series: "fashion-archive"
chapter: "troubleshooting"
seriesOrder: 1
---
## 해결한 문제

처음 설계는 사진을 **Supabase Storage**(외부 파일 저장 서비스)에 올리고 DB에는 공개 주소만 저장하는 방식이었습니다. 설계 문서의 결정 표에도 "서버 경유 업로드 → Supabase Storage (REST, Java 표준 `HttpClient`)"로 적혀 있습니다.

2026-09-21의 커밋 `e9b57d5` "사진 저장소를 Supabase에서 로컬 디스크로 전환"에서 사진을 앱이 실행되는 **서버의 디스크**에 저장하고 nginx가 직접 보내는 방식으로 바꿨습니다.

## 바뀐 파일

```text
Dockerfile                                       |  9 +-
README.md                                        | 19 +++--
deploy/OPERATIONS.md                             | 80 ++++++++++++++++++
deploy/nginx-fashion-archive.conf                | 20 +++++
docker-compose.yml                               | 18 ++++
.../com/fashion/cmmn/storage/LocalStorage.java   | 58 +++++++++++++
.../com/fashion/cmmn/storage/SupabaseStorage.java| 97 ----------------------
.../photo/controller/PhotoApiController.java     |  4 +-
src/main/resources/egovProps/globals.properties  |  7 +-
```

저장 클래스를 바꾸면서 서버 실행 파일(compose, nginx 설정, 운영 문서)이 함께 추가되었습니다.

## 컨트롤러는 4줄만

`PhotoApiController`의 변경

```diff
-import com.fashion.cmmn.storage.SupabaseStorage;
+import com.fashion.cmmn.storage.LocalStorage;
-    private SupabaseStorage storage;
+    private LocalStorage storage;
```

저장소를 통째로 바꿨는데 컨트롤러는 클래스 이름만 바뀌었습니다. 컨트롤러가 저장소에 요구하는 일이 두 가지뿐이고, 새 클래스가 같은 이름과 모양으로 그 일을 하기 때문입니다.

| 메서드 | 하는 일 |
|---|---|
| `upload(...)` → 공개 주소 | 파일을 저장하고, DB에 넣을 주소를 돌려줌 |
| `delete(공개 주소)` | 주소로 파일을 찾아 지움. 실패해도 예외를 던지지 않음 |

DB에는 처음부터 **파일이 아니라 공개 주소**(`photo.image_url`)를 저장했습니다. 그래서 저장 위치가 바뀌어도 테이블 구조는 그대로입니다. 업로드 순서(검증 → 파일 저장 → DB 트랜잭션, 실패 시 파일 정리)도 그대로 유지되었습니다([파일과 DB 맞추기](/posts/fashion-archive-upload-consistency)).

## 설정의 변화

`globals.properties`

```diff
-# 비어 있으면 기동은 되지만 사진 업로드 시 "저장소 미설정" 오류가 난다
-Globals.Fashion.Supabase.Url=${FASHION_SUPABASE_URL:}
-Globals.Fashion.Supabase.ServiceKey=${FASHION_SUPABASE_SERVICE_KEY:}
-Globals.Fashion.Supabase.Bucket=${FASHION_SUPABASE_BUCKET:photos}
+# 사진 저장 경로(서버 로컬 디스크)와 그 경로를 서빙하는 공개 URL. Url이 비어 있으면 업로드 시 "저장소 미설정" 오류가 난다
+Globals.Fashion.Upload.Dir=${FASHION_UPLOAD_DIR:/data/fashion-uploads}
+Globals.Fashion.Upload.BaseUrl=${FASHION_UPLOAD_BASE_URL:}
```

외부 서비스의 주소와 서비스 키 대신, 저장 폴더와 공개 주소 두 값이 되었습니다. "설정이 비어 있으면 앱은 시작되지만 업로드할 때 분명한 오류를 낸다"는 규칙은 그대로입니다(그 규칙은 커밋 `9dccedc`에서 Supabase용으로 처음 들어왔습니다).

## 서버 쪽에서 함께 필요해진 것

| 파일 | 역할 |
|---|---|
| `docker-compose.yml`의 `volumes` | 컨테이너 안 저장 폴더를 서버 폴더(`/srv/fashion-uploads`)에 연결해 컨테이너를 새로 만들어도 사진 유지 |
| `deploy/nginx-fashion-archive.conf`의 `/uploads/` | 서버 폴더의 사진을 nginx가 직접 보냄 |
| `deploy/OPERATIONS.md` | 업로드 폴더와 운영 명령 정리 |

저장 위치를 직접 관리하게 되면서, 파일 권한(다음 글)과 사용자별 폴더, 경로 공격 방어 같은 일도 앱이 맡게 되었습니다([디스크 저장](/posts/fashion-archive-upload-local-storage)).

## 확인 방법

- 사진을 올린 뒤 상세 화면에서 사진이 보이는지, 이미지 주소가 `/uploads/{아이디}/{UUID}.{확장자}` 형식인지 확인합니다.
- 서버의 `/srv/fashion-uploads/{아이디}/`에 같은 이름의 파일이 생겼는지 확인합니다.
- 사진을 지운 뒤 파일도 지워졌는지 확인합니다.

기록은 [전환 커밋](https://github.com/Dongsik0000/fashion-archive/commit/e9b57d50ce89c5cbe4bd77b2eb6ca2e29d0babbf)과 현재 `LocalStorage.java`, `globals.properties`, `docker-compose.yml`을 근거로 작성했습니다.
