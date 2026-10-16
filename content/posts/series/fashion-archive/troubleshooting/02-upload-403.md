---
title: "업로드한 사진이 403으로 깨지던 문제"
slug: "fashion-archive-troubleshooting-upload-403"
category: "development"
publishedAt: "2026-10-08"
summary: "Tomcat의 umask 0027 때문에 업로드 파일이 0640, 폴더가 0750으로 만들어져 nginx(www-data)가 읽지 못하고 403을 내던 문제와, 파일 644와 폴더 755 권한을 코드에서 직접 지정한 두 번의 수정을 설명합니다."
series: "fashion-archive"
chapter: "troubleshooting"
seriesOrder: 2
---
## 해결한 문제

서버에서 사진을 올리면 업로드는 성공하는데, 화면의 사진이 깨지고 사진 주소가 **403 Forbidden**을 내는 문제가 있었습니다. 2026-09-27의 커밋 `5865311`이 원인을 기록합니다.

> 업로드 사진 권한을 644로 지정: Tomcat umask(0027)로 0640이 되어 nginx가 읽지 못해 이미지가 403으로 깨지던 문제

## 원인: 쓰는 사용자와 읽는 사용자가 다름

```text
쓰기: Tomcat (컨테이너 안) → /data/fashion-uploads/... = 서버의 /srv/fashion-uploads/...
읽기: nginx (서버, www-data 사용자) → /uploads/... 요청에 그 파일을 보냄
```

파일을 만드는 쪽과 읽는 쪽이 다른 사용자입니다. 리눅스는 파일 권한을 **소유자, 그룹, 그 밖의 사용자**로 나누는데, nginx는 이 파일의 "그 밖의 사용자"입니다.

### umask

새 파일의 권한은 프로그램이 요청한 권한에서 **umask**에 적힌 비트를 뺀 값입니다. 커밋 메시지가 기록한 Tomcat의 umask는 `0027`입니다.

| | 요청 | umask 0027 | 결과 | 그 밖의 사용자 |
|---|---|---|---|---|
| 파일 | `666` (rw-rw-rw-) | `027` 빼기 | `640` (rw-r-----) | **읽기 불가** |
| 폴더 | `777` (rwxrwxrwx) | `027` 빼기 | `750` (rwxr-x---) | **들어갈 수 없음** |

nginx는 파일을 읽을 수 없어 403을 냅니다. 앱 쪽에서는 저장이 성공했으므로 오류가 없고, 화면에서 사진이 깨지는 것으로만 드러납니다.

## 수정 1: 파일 644 (2026-09-27)

```diff
-        Files.write(dir.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
+        Path file = dir.resolve(name);
+        Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
+        // Tomcat 기본 umask(0027)로 생기는 0640 파일은 nginx(www-data)가 읽지 못한다
+        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
```

파일을 쓴 직후 `rw-r--r--`(644)로 바꿔 그 밖의 사용자도 읽을 수 있게 합니다. `Files.setPosixFilePermissions`는 umask와 상관없이 지정한 권한을 그대로 적용합니다.

## 수정 2: 사용자 폴더 755 (2026-09-29)

이틀 뒤 커밋 `38f6f95`("계정별 사진 관리")에서 저장 위치가 사용자별 폴더(`/{아이디}/`)로 바뀌었습니다. 이번에는 Tomcat이 **폴더**를 새로 만들게 되었고, 폴더도 같은 umask로 `750`이 됩니다. 파일이 644여도 폴더에 들어갈 수 없으면 읽지 못합니다. 그래서 폴더 권한도 함께 지정했습니다.

`com/fashion/cmmn/storage/LocalStorage.java` (현재)

```java
Path dir = Paths.get(uploadDir, loginId);
Files.createDirectories(dir);
// Tomcat 기본 umask(0027)로 생기는 0750 폴더·0640 파일은 nginx(www-data)가 읽지 못한다
Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
Path file = dir.resolve(name);
Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
```

커밋 메시지에도 "폴더 권한 755 지정(umask 0027로 0750이 되면 nginx가 읽지 못함)"이 남아 있습니다. 폴더를 만드는 코드 바로 다음 줄에서 권한을 지정합니다.

## 다른 해결 방법과 비교

| 방법 | 내용 |
|---|---|
| **코드에서 권한 지정 (적용)** | 앱이 만드는 파일과 폴더에만 정확히 적용. 앱 코드에 이유가 주석으로 남음 |
| Tomcat umask 바꾸기 | Tomcat이 만드는 **모든** 파일(로그 등)의 권한이 함께 넓어짐 |
| nginx를 같은 그룹에 넣기 | 서버 설정과 컨테이너의 사용자 그룹을 맞춰야 해 서버마다 설정이 필요 |

## 확인 방법

1. 사진을 하나 올립니다.
2. 서버에서 권한을 확인합니다.

```bash
ls -ld /srv/fashion-uploads/{아이디}
ls -l /srv/fashion-uploads/{아이디}/
```

| 대상 | 기대 |
|---|---|
| 사용자 폴더 | `drwxr-xr-x` |
| 사진 파일 | `-rw-r--r--` |

3. 브라우저 개발자 도구의 Network에서 사진 요청이 200인지 확인합니다.

기록은 [파일 권한 커밋](https://github.com/Dongsik0000/fashion-archive/commit/58653111b266bf1792d70bb912ab2c26527342f9)과 커밋 `38f6f95`, 현재 `LocalStorage.java`를 근거로 작성했습니다.
