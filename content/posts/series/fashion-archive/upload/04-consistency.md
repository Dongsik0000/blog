---
title: "파일과 DB를 함께 맞추기"
slug: "fashion-archive-upload-consistency"
category: "development"
publishedAt: "2026-10-08"
summary: "파일 저장과 DB 저장은 하나의 트랜잭션으로 묶을 수 없습니다. 등록은 파일을 먼저 쓰고 DB 저장이 실패하면 파일을 지우고, 삭제는 DB를 먼저 지우고 파일을 나중에 지우는 순서로 \"DB가 가리키는 파일은 항상 있다\"를 지키는 방법을 설명합니다."
series: "fashion-archive"
chapter: "upload"
seriesOrder: 4
---
## 이 글에서 할 일

사진을 등록하면 **디스크에 파일**을 쓰고 **DB에 행**을 넣습니다. DB 작업은 트랜잭션으로 묶어 "모두 성공 아니면 모두 취소"를 만들 수 있지만, 디스크 파일은 DB 트랜잭션에 들어가지 않습니다. 둘 중 하나만 성공하면 어떻게 될까요?

| 남는 상태 | 결과 |
|---|---|
| DB 행은 있는데 파일이 없음 | 화면에 **깨진 사진**이 보임 |
| 파일은 있는데 DB 행이 없음 | 화면에는 문제 없음. 디스크에 쓰지 않는 파일(**고아 파일**)이 남음 |

Fashion Archive는 **첫 번째 상태는 절대 만들지 않고, 두 번째는 허용**하는 쪽으로 순서를 정했습니다. `LocalStorage` 주석의 "고아 파일은 허용, DB 불일치는 불허"가 그 원칙입니다.

## 등록: 파일 먼저, 실패하면 파일 정리

`PhotoApiController.create` (일부)

```java
// 등록. multipart: file, title, memo, categoryIds. 흐름: 검증 → Storage 업로드 → (트랜잭션) INSERT ×2
...
String imageUrl = storage.upload((String) session.getAttribute(Constants.SESSION_LOGIN_ID), bytes, ext);

Map<String, Object> photo = new HashMap<String, Object>();
photo.put("ownerId", ownerId(session));
photo.put("title", cleanTitle);
photo.put("memo", cleanMemo);
photo.put("imageUrl", imageUrl);

long id;
try {
    // INSERT 둘 중 하나라도 실패하면 둘 다 롤백
    id = transactionTemplate.execute(status -> {
        photoService.insertPhoto(photo);
        long newId = ((Number) photo.get("id")).longValue();
        photoService.insertPhotoCategories(newId, ids);
        return newId;
    });
} catch (RuntimeException e) {
    storage.delete(imageUrl);   // DB에 못 남겼으니 방금 올린 파일도 정리
    throw e;
}
```

```text
1. 입력 검증 (제목, 메모, 카테고리, 파일 형식)  ← 실패하면 아무것도 안 함
2. 파일 저장 → 공개 주소
3. 트랜잭션 { 사진 INSERT, 카테고리 연결 INSERT }
   ├─ 성공 → 끝
   └─ 실패 → DB는 롤백, 2단계의 파일도 지움, 예외는 다시 던짐
```

### 왜 파일을 먼저 쓰나

DB에 넣으려면 `image_url`(공개 주소)이 필요하고, 주소는 파일을 저장해야 정해집니다(UUID 이름). 그리고 순서를 반대로 하면 "DB 행은 있는데 파일이 없는" 순간이 생깁니다. 파일을 먼저 쓰면 DB에 행이 생기는 순간 파일은 이미 있습니다.

### 검증을 가장 먼저

입력 검증을 파일 저장 **전에** 모두 끝냅니다. 제목이 비었다는 이유로 실패할 요청이라면 파일을 쓸 필요가 없습니다. 검증 뒤에 실패할 수 있는 것은 DB 오류 정도만 남습니다.

### 실패하면 파일을 지우고 예외는 다시 던짐

```java
} catch (RuntimeException e) {
    storage.delete(imageUrl);
    throw e;
}
```

`TransactionTemplate.execute`는 람다 안에서 `RuntimeException`이 나면 DB를 롤백하고 그 예외를 다시 던집니다. 이 `catch`는 **파일만 정리하고 예외를 그대로 다시 던져**, 바깥의 `catch (Exception e)`가 로그를 남기고 "사진을 올리지 못했습니다"로 응답하게 합니다. 정리를 하면서도 실패 사실은 숨기지 않습니다.

`storage.delete`는 실패해도 예외를 던지지 않으므로([디스크 저장](/posts/fashion-archive-upload-local-storage)), 정리 중 오류가 원래 예외를 가리지 않습니다.

## 삭제: DB 먼저, 파일 나중

`PhotoApiController.delete`

```java
// 삭제. DB(CASCADE)가 먼저, 파일은 나중에. 파일 삭제 실패는 고아 파일로 남을 뿐 화면에는 영향 없음
@ResponseBody
@PostMapping("/api/admin/photos/{id}/delete")
public Response delete(@PathVariable long id, HttpSession session) {
    try {
        long ownerId = ownerId(session);
        Map<String, Object> photo = photoService.selectPhoto(id, ownerId);
        if (photo == null || photoService.deletePhoto(id, ownerId) == 0) {
            return Response.of(Constants.FAIL, "사진을 찾을 수 없습니다.", null);
        }
        storage.delete((String) photo.get("image_url"));
        return Response.of(Constants.SUCCESS);
    } catch (Exception e) {
        ...
    }
}
```

```text
1. 사진 조회 (내 사진인지 + 파일 주소 확보)
2. DB에서 사진 삭제 → 카테고리 연결과 제품 링크는 ON DELETE CASCADE로 함께 삭제
3. 파일 삭제 (실패해도 로그만)
```

순서를 반대로 해서 파일을 먼저 지웠는데 DB 삭제가 실패하면, 화면에는 사진이 남아 있는데 파일이 없어 **깨진 사진**이 됩니다. DB를 먼저 지우면, 그 뒤 파일 삭제가 실패해도 화면에서는 이미 사라졌고 고아 파일만 남습니다.

DB 삭제는 SQL 한 줄(`DELETE FROM photo ...`)이고 나머지는 `CASCADE`가 처리하므로 트랜잭션을 따로 걸 필요가 없습니다.

## 두 작업의 공통 원칙

| 작업 | 순서 | 실패했을 때 남는 것 |
|---|---|---|
| 등록 | 검증 → 파일 → DB | DB 실패 시 파일도 정리. 정리까지 실패하면 고아 파일 |
| 삭제 | DB → 파일 | 파일 삭제 실패 시 고아 파일 |

어느 경우든 "**DB가 가리키는 파일은 반드시 있다**"가 지켜집니다. 고아 파일은 사용자에게 보이지 않고, 필요하면 DB의 `image_url` 목록과 디스크를 비교해 나중에 정리할 수 있습니다.

## 정리

- 파일과 DB는 한 트랜잭션으로 묶을 수 없으므로, 실패했을 때 남을 상태를 순서로 정합니다.
- 등록은 검증 → 파일 → DB 트랜잭션 순서이고, DB가 실패하면 방금 쓴 파일을 지운 뒤 예외를 다시 던집니다.
- 삭제는 DB를 먼저 지우고(CASCADE), 파일은 나중에 지웁니다.
- 결과적으로 깨진 사진은 생기지 않고, 최악의 경우 고아 파일만 남습니다.
