---
title: "본인 사진만 조회하고 수정하기"
slug: "fashion-archive-database-owner-scope"
category: "development"
publishedAt: "2026-10-08"
summary: "모든 사진 SQL에 owner_id 조건을 거는 방법, 소유자 칸이 없는 제품 링크는 사진의 owner_id로 확인하는 하위 쿼리, 사용자 id를 세션에서만 꺼내는 이유, 남의 사진과 없는 사진에 같은 응답을 주는 이유를 설명합니다."
series: "fashion-archive"
chapter: "database"
seriesOrder: 5
---
## 이 글에서 할 일

처음 설계는 관리자 한 명이 사진을 올리고 누구나 보는 사이트였습니다. 이후 커밋 `38f6f95`("계정별 사진 관리")에서 **로그인한 사용자가 자기 사진만 보고 고치는** 구조로 바뀌었습니다. 이 글에서는 그 규칙을 SQL과 코드로 어떻게 지키는지 봅니다.

## 규칙 1. 사진 SQL에는 모두 owner_id

`photo.xml`

```xml
<!-- 사진 목록. 로그인한 사용자의 사진만 -->
WHERE p.owner_id = #{ownerId}

<!-- 사진 1장. 다른 사용자의 사진이면 결과 없음 -->
WHERE id = #{id}
AND owner_id = #{ownerId}

UPDATE photo SET ... WHERE id = #{id} AND owner_id = #{ownerId}

DELETE FROM photo WHERE id = #{id} AND owner_id = #{ownerId}
```

id만으로 찾지 않고 **id와 주인을 함께** 조건으로 겁니다. 남의 사진 id를 보내면 조건에 맞는 행이 없어 조회는 `null`, 수정과 삭제는 0줄입니다. 따로 "주인인가?"를 검사하는 코드 없이 SQL이 막습니다.

## 규칙 2. 링크는 사진의 주인으로 확인

`product_link`에는 주인 칸이 없습니다. 링크는 사진에 속하므로, **링크가 속한 사진의 주인**으로 확인합니다.

`link.xml`

```xml
<!-- 수정·삭제: 링크에는 소유자 칸이 없어서 사진의 owner_id로 확인한다 -->
<update id="updateLink" parameterType="java.util.HashMap">
    UPDATE product_link
       SET item_label = #{itemLabel},
           url        = #{url},
           title      = #{title},
           note       = #{note}
     WHERE id = #{id}
       AND photo_id IN (SELECT id FROM photo WHERE owner_id = #{ownerId})
</update>
```

`photo_id IN (SELECT id FROM photo WHERE owner_id = #{ownerId})`는 "이 링크가 내 사진들 중 하나에 속한다"는 조건입니다. 링크 id만 알아서는 남의 링크를 고칠 수 없습니다.

링크를 **추가**할 때는 사진이 내 것인지 먼저 확인합니다.

```java
Map<String, Object> link = linkParam(param);
if (photoService.selectPhoto(photoId, ownerId(session)) == null) {
    return Response.of(Constants.FAIL, "사진을 찾을 수 없습니다.", null);
}
link.put("photoId", photoId);
photoService.insertLink(link);
```

## 규칙 3. 사용자 id는 세션에서만

```java
// 로그인한 사용자 id. CmmnApiController.login이 세션에 넣는다
private static long ownerId(HttpSession session) {
    return (Long) session.getAttribute(Constants.SESSION_USER_ID);
}
```

`ownerId`는 요청 본문에서 받지 않고 항상 세션에서 꺼냅니다. 요청 본문의 값은 사용자가 개발자 도구로 바꿀 수 있지만, 세션의 값은 로그인할 때 서버가 넣은 것이라 바꿀 수 없습니다.

API 컨트롤러는 요청 Map을 DAO에 그대로 넘기지 않고, 검증한 값과 세션의 `ownerId`로 **새 Map을 만들어** 넘깁니다.

```java
Map<String, Object> photo = new HashMap<String, Object>();
photo.put("id", id);
photo.put("ownerId", ownerId(session));
photo.put("title", title);
photo.put("memo", memo);
```

요청 본문에 `"ownerId": 2`를 끼워 넣어도 DAO에 전달되지 않습니다.

`LoginInterceptor`가 로그인하지 않은 요청을 미리 막으므로, API 메서드 안에서는 세션에 `userId`가 반드시 있습니다([인터셉터](/posts/fashion-archive-spring-mvc-interceptor)).

## 규칙 4. 남의 것과 없는 것은 같은 응답

```java
// 사진 상세: 사진 + 카테고리 id + 라벨별 링크. 다른 사용자의 사진은 없는 사진과 같은 응답
Map<String, Object> photo = photoService.selectPhoto(id, ownerId(session));
if (photo == null) {
    return Response.of(Constants.FAIL, "사진을 찾을 수 없습니다.", null);
}
```

"다른 사람의 사진입니다"라고 따로 알려 주면, id를 바꿔 가며 "어떤 id의 사진이 존재하는지" 알아낼 수 있습니다. 두 경우 모두 "찾을 수 없습니다"로 답해 정보를 흘리지 않습니다.

## 사진 파일 폴더도 사용자별

DB뿐 아니라 사진 파일도 사용자별 폴더에 저장합니다(`{업로드 폴더}/{아이디}/{파일}`). 삭제할 때도 `{아이디}/{파일}` 형식의 주소만 처리합니다([디스크 저장](/posts/fashion-archive-upload-local-storage)).

## 정리

- 사진의 조회, 수정, 삭제 SQL에 모두 `owner_id = #{ownerId}` 조건을 겁니다.
- 주인 칸이 없는 링크는 `photo_id IN (내 사진)` 하위 쿼리로 확인하고, 추가 전에는 사진이 내 것인지 조회합니다.
- `ownerId`는 세션에서만 꺼내고, DAO에는 검증한 값으로 새 Map을 만들어 넘깁니다.
- 남의 사진과 없는 사진은 같은 응답으로 답합니다.
