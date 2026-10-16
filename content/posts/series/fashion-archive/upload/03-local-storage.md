---
title: "사용자별 폴더에 저장하기 (파일 이름, 권한, 경로 공격 방어)"
slug: "fashion-archive-upload-local-storage"
category: "development"
publishedAt: "2026-10-08"
summary: "사진을 {업로드 폴더}/{아이디}/{UUID}.{확장자}로 저장하는 LocalStorage를 읽으며, 아이디를 폴더 이름으로 쓰기 위한 규칙, UUID 파일 이름, nginx가 읽을 수 있게 권한을 지정하는 이유, 삭제할 때 경로를 벗어나지 못하게 하는 검사를 설명합니다."
series: "fashion-archive"
chapter: "upload"
seriesOrder: 3
---
## 이 글에서 할 일

올린 사진은 서버 디스크에 저장하고, DB에는 그 사진의 공개 주소만 남깁니다. 파일을 다루는 코드는 보안 문제가 생기기 쉬운 곳입니다. 이 글에서는 `LocalStorage`가 파일을 어디에, 어떤 이름과 권한으로 저장하고 지우는지 봅니다.

## 저장 위치와 공개 주소

```text
디스크:  {FASHION_UPLOAD_DIR}/{아이디}/{UUID}.{확장자}
주소:    {FASHION_UPLOAD_BASE_URL}/{아이디}/{UUID}.{확장자}   ← DB의 image_url
```

| 설정 | 컨테이너 안 | 서버 |
|---|---|---|
| 저장 폴더 | `/data/fashion-uploads` (기본값) | compose 볼륨으로 `/srv/fashion-uploads`에 연결 |
| 공개 주소 | `FASHION_UPLOAD_BASE_URL` | nginx가 `/uploads/`로 `/srv/fashion-uploads/`를 보냄 |

Tomcat은 파일을 쓰기만 하고, 사진을 보여 주는 일은 nginx가 합니다([nginx 연결](/posts/fashion-archive-deploy-nginx)).

## 저장: upload

`com/fashion/cmmn/storage/LocalStorage.java`

```java
// 로컬 디스크 저장. 업로드: {uploadDir}/{loginId}/{file} 쓰기, 공개 URL: {baseUrl}/{loginId}/{file} (nginx가 정적 파일로 서빙)
@Component
public class LocalStorage {

    @Value("${Globals.Fashion.Upload.Dir}")
    private String uploadDir;

    @Value("${Globals.Fashion.Upload.BaseUrl}")
    private String baseUrl;

    // 사용자 폴더에 업로드 후 공개 URL 반환. 폴더는 첫 업로드 때 만든다. 실패하면 IOException
    public String upload(String loginId, byte[] bytes, String ext) throws IOException {
        if (uploadDir == null || uploadDir.isEmpty() || baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("사진 저장소가 설정되지 않았습니다. FASHION_UPLOAD_DIR / FASHION_UPLOAD_BASE_URL을 확인하세요.");
        }
        // 가입 때 검증하지만, 규칙 이전에 만든 계정도 있으므로 폴더를 만들기 전에 다시 확인한다
        if (!Validation.isLoginId(loginId)) {
            throw new IllegalArgumentException("이 계정의 아이디로는 사진 폴더를 만들 수 없습니다. 관리자에게 문의해주세요.");
        }
        String name = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(uploadDir, loginId);
        Files.createDirectories(dir);
        // Tomcat 기본 umask(0027)로 생기는 0750 폴더·0640 파일은 nginx(www-data)가 읽지 못한다
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path file = dir.resolve(name);
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        return baseUrl + "/" + loginId + "/" + name;
    }
```

### 1. 설정이 비었으면 분명하게 실패

저장 폴더나 공개 주소가 비어 있으면 저장을 시도하지 않고 원인을 적은 문장으로 실패합니다. 앱은 설정 없이도 시작되고(조회는 가능), 업로드할 때 무엇을 설정해야 하는지 알려 줍니다(커밋 `9dccedc` "start without supabase config, fail clearly on upload"에서 시작한 방식).

### 2. 아이디를 폴더 이름으로 쓰기

```java
// 아이디는 사진 폴더 이름(/srv/fashion-uploads/{아이디})으로도 쓰인다. 경로 문자(/, .)가 들어갈 수 없게 제한한다
private static final Pattern LOGIN_ID = Pattern.compile("[a-z0-9_-]{3,30}");
```

아이디가 폴더 이름이 되므로, 아이디에 `/`나 `..`이 들어가면 다른 폴더에 파일을 쓸 수 있습니다. 가입할 때 아이디를 **영문 소문자, 숫자, `_`, `-`만** 허용하고, 저장 직전에 한 번 더 확인합니다. 주석대로 이 규칙이 생기기 전에 만든 계정이 있을 수 있기 때문입니다.

### 3. 파일 이름은 UUID

```java
String name = UUID.randomUUID() + "." + ext;
```

사용자가 올린 파일 이름(`IMG_0412.jpg`)은 쓰지 않습니다.

| 사용자 파일 이름을 쓰면 | UUID를 쓰면 |
|---|---|
| 같은 이름이면 덮어씀 | 겹치지 않음 |
| `../../etc/x` 같은 이름으로 경로 공격 가능 | 이름에 경로 문자가 없음 |
| 한글, 공백으로 주소가 복잡해짐 | 영문과 숫자, `-`만 |
| 주소에서 다른 사람 파일 이름을 짐작하기 쉬움 | 짐작할 수 없음 |

확장자 `ext`는 [파일 내용으로 판별한 값](/posts/fashion-archive-upload-file-type)입니다.

### 4. 새 파일로만 쓰기

```java
Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
```

`CREATE_NEW`는 같은 이름의 파일이 이미 있으면 **덮어쓰지 않고 실패**합니다. UUID라 겹칠 일은 거의 없지만, 혹시 겹쳐도 기존 사진을 지우지 않습니다.

### 5. nginx가 읽을 수 있는 권한

```java
// Tomcat 기본 umask(0027)로 생기는 0750 폴더·0640 파일은 nginx(www-data)가 읽지 못한다
Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
...
Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
```

| 대상 | 권한 | 뜻 |
|---|---|---|
| 사용자 폴더 | `rwxr-xr-x` (755) | 다른 사용자도 들어가서 목록을 볼 수 있음 |
| 사진 파일 | `rw-r--r--` (644) | 다른 사용자도 읽을 수 있음 |

파일을 쓰는 쪽(Tomcat)과 읽는 쪽(nginx, `www-data` 사용자)이 다른 사용자입니다. Tomcat의 기본 설정대로 만들면 다른 사용자는 읽을 수 없어, nginx가 403을 냈습니다. 그래서 권한을 직접 지정합니다. 이 문제를 찾은 과정은 [사진 403 문제](/posts/fashion-archive-troubleshooting-upload-403)에 있습니다.

## 삭제: delete

```java
// 실패해도 예외를 던지지 않는다 (고아 파일은 허용, DB 불일치는 불허)
public void delete(String publicUrl) {
    String marker = (baseUrl == null || baseUrl.isEmpty()) ? null : baseUrl + "/";
    int idx = (publicUrl == null || marker == null) ? -1 : publicUrl.indexOf(marker);
    if (idx < 0) {
        logger.warn("Storage delete skipped, unexpected url: {}", publicUrl);
        return;
    }
    String name = publicUrl.substring(idx + marker.length());
    Path file = storedPath(uploadDir, name);
    if (file == null) {
        logger.warn("Storage delete skipped, unsafe file name: {}", name);
        return;
    }
    try {
        Files.deleteIfExists(file);
    } catch (IOException e) {
        logger.warn("Storage delete failed: {}", name, e);
    }
}
```

DB의 공개 주소에서 `{아이디}/{파일}` 부분을 떼어 디스크 경로로 바꾼 뒤 지웁니다.

### 경로를 벗어나지 못하게

```java
// "{loginId}/{file}" → 디스크 경로. 형식이 다르거나 경로를 벗어날 수 있는 이름이면 null
static Path storedPath(String uploadDir, String name) {
    int slash = name.indexOf('/');
    if (slash < 0) {
        return null;
    }
    String folder = name.substring(0, slash);
    String file = name.substring(slash + 1);
    if (!Validation.isLoginId(folder) || file.isEmpty() || file.contains("/") || file.contains("..")) {
        return null;
    }
    return Paths.get(uploadDir, folder, file);
}
```

`{아이디}/{파일}` 형식이 정확히 맞을 때만 경로를 만듭니다.

| 이름 | 결과 |
|---|---|
| `minji/3f2a….jpg` | 저장 폴더 안의 파일 |
| `minji/../../etc/passwd` | 파일 부분에 `/`가 있어 거절 |
| `../x.jpg` | 폴더 부분이 아이디 규칙에 맞지 않아 거절 |
| `x.jpg` | `/`가 없어 거절 |

DB 값은 앱이 넣은 것이지만, 어떤 이유로 이상한 값이 들어 있어도 저장 폴더 밖의 파일을 지울 수 없게 합니다. 이 검사는 `LocalStorageTest`로 확인합니다.

### 삭제 실패는 예외로 만들지 않음

삭제는 DB에서 사진을 지운 **뒤에** 합니다. 이때 파일 삭제가 실패해도 사진은 이미 화면에서 사라졌고, 디스크에 쓰지 않는 파일이 하나 남을 뿐입니다. 주석대로 "고아 파일은 허용, DB 불일치는 불허"입니다. 순서의 이유는 [파일과 DB 맞추기](/posts/fashion-archive-upload-consistency)에서 봅니다.

## 정리

- 사진은 `{저장 폴더}/{아이디}/{UUID}.{확장자}`에 쓰고, DB에는 공개 주소를 저장합니다.
- 아이디는 경로 문자가 없게 제한하고, 파일 이름은 UUID, 확장자는 내용 판별 결과로 정합니다.
- nginx가 읽을 수 있도록 폴더 755, 파일 644 권한을 직접 지정합니다.
- 삭제는 `{아이디}/{파일}` 형식만 허용해 저장 폴더 밖을 건드리지 못하게 하고, 실패해도 예외를 던지지 않습니다.
