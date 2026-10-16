---
title: "확장자가 아니라 파일 내용으로 이미지 형식 판별하기"
slug: "fashion-archive-upload-file-type"
category: "development"
publishedAt: "2026-10-08"
summary: "파일 이름의 확장자나 브라우저가 보낸 Content-Type을 믿지 않고, 파일 앞 12바이트의 매직 넘버로 jpg, png, webp를 판별하는 Validation.imageExtension을 바이트 단위로 설명합니다."
series: "fashion-archive"
chapter: "upload"
seriesOrder: 2
---
## 이 글에서 할 일

사진만 올릴 수 있어야 하는데, 어떻게 "이 파일이 사진인지" 알 수 있을까요? 파일 이름 `photo.jpg`의 확장자나, 브라우저가 붙여 보내는 `Content-Type: image/jpeg`는 **사용자가 마음대로 바꿀 수 있습니다.** HTML 파일 이름을 `photo.jpg`로 바꿔 올릴 수도 있습니다. Fashion Archive는 **파일 내용의 앞부분**을 직접 읽어 형식을 판별합니다.

## 매직 넘버

대부분의 파일 형식은 파일 맨 앞에 정해진 바이트(**매직 넘버**, 파일 서명)가 있습니다.

| 형식 | 앞부분 바이트 | 글자로 보면 |
|---|---|---|
| JPEG | `FF D8 FF` | (글자 아님) |
| PNG | `89 50 4E 47` | `.PNG` |
| WebP | `52 49 46 46` ?? ?? ?? ?? `57 45 42 50` | `RIFF....WEBP` |

WebP는 RIFF라는 공통 컨테이너 형식을 써서, 앞 4바이트는 `RIFF`, 그다음 4바이트는 파일 크기, 9~12번째 바이트가 `WEBP`입니다. 그래서 판별에 앞 **12바이트**가 필요합니다.

## 코드

`com/fashion/cmmn/util/Validation.java`

```java
// 확장자가 아니라 파일 앞 12바이트로 형식을 판별한다
public static String imageExtension(byte[] head) {
    if (head != null && head.length >= 12) {
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
    }
    throw new IllegalArgumentException("jpg, png, webp 이미지만 등록할 수 있습니다.");
}
```

### `& 0xFF`가 필요한 이유

Java의 `byte`는 **부호가 있는** 8비트 정수라 -128 ~ 127입니다. 바이트 `0xFF`(255)는 Java에서 `-1`로 읽힙니다.

```java
byte b = (byte) 0xFF;
b == 0xFF          // false. b는 -1, 0xFF는 int 255
(b & 0xFF) == 0xFF // true.  -1 & 0xFF = 255
```

`& 0xFF`는 부호를 버리고 0 ~ 255 범위의 `int`로 바꿉니다. `0x80` 이상의 바이트(`0xFF`, `0xD8`, `0x89`)를 비교할 때는 꼭 필요하고, `'P'`(0x50)처럼 128보다 작은 값은 그대로 비교해도 같습니다.

### 판별 결과로 확장자를 정함

```java
byte[] bytes = file.getBytes();
String ext = Validation.imageExtension(Arrays.copyOf(bytes, 12));   // 확장자가 아니라 파일 내용으로 판별
...
String imageUrl = storage.upload(loginId, bytes, ext);
```

- `Arrays.copyOf(bytes, 12)`: 앞 12바이트만 잘라 넘깁니다. 파일이 12바이트보다 짧으면 나머지를 0으로 채운 배열이 되어, 어떤 형식에도 맞지 않아 거절됩니다.
- 저장할 파일 이름의 확장자는 **사용자가 올린 이름이 아니라 판별 결과**(`jpg`, `png`, `webp`)로 정합니다. 사용자가 올린 파일 이름은 쓰지 않습니다([디스크 저장](/posts/fashion-archive-upload-local-storage)).

## 왜 이렇게까지 하나

사진은 nginx가 `/uploads/` 아래에서 정적 파일로 보냅니다. HTML이나 SVG 같은 파일이 사진으로 위장해 올라가고, 브라우저가 그것을 HTML로 해석하면 그 파일 안의 스크립트가 이 사이트 주소에서 실행될 수 있습니다. 형식을 내용으로 확인하고, 확장자를 판별 결과로 붙이고, nginx가 `X-Content-Type-Options: nosniff`를 붙이는 것이 함께 이를 막습니다([nginx 연결](/posts/fashion-archive-deploy-nginx)).

## 테스트

`ValidationTest`가 판별을 검사합니다.

```java
@Test
void imageExtension_rejectsOthers() {
    assertThrows(IllegalArgumentException.class, () -> Validation.imageExtension(new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0}));
    assertThrows(IllegalArgumentException.class, () -> Validation.imageExtension(new byte[]{1, 2}));
    assertThrows(IllegalArgumentException.class, () -> Validation.imageExtension(null));
}
```

GIF(`GIF89a`), 너무 짧은 배열, `null`을 모두 거절하는지 확인합니다.

## 정리

- 확장자와 `Content-Type`은 사용자가 바꿀 수 있으므로, 파일 앞 12바이트의 매직 넘버로 형식을 판별합니다.
- JPEG `FF D8 FF`, PNG `.PNG`, WebP `RIFF....WEBP`를 확인합니다.
- Java `byte`는 부호가 있어 `0x80` 이상을 비교할 때 `& 0xFF`로 바꿉니다.
- 저장 파일의 확장자는 판별 결과로 정합니다.
