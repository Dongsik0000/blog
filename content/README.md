# DevGear 글 원본

AI(GPT·Claude)가 쓰는 글은 `content/posts/`에 Markdown 파일로 커밋합니다. 파일명은 `YYYY-MM-DD-영문-슬러그.md` 형식을 권장합니다.

이 폴더는 **입력함**입니다. 앱이 시작할 때 DB에 같은 slug가 없는 파일만 가져오고, 그 뒤로는 블로그 관리 화면(`/blog/admin/posts`)에서 고칩니다. 가져온 뒤 파일을 바꿔도 사이트에는 반영되지 않습니다. 관리 화면에서 지운 글도 slug가 남아 다시 들어오지 않습니다.

매일 IT 뉴스 글을 쓰는 AI 프롬프트는 두 가지입니다. 이 문서의 형식 규칙이 기준이며, 규칙을 바꾸면 프롬프트도 함께 고칩니다.

- [NEWS_PROMPT_CLAUDE.md](NEWS_PROMPT_CLAUDE.md): 글만 커밋. 이미지는 댓글의 4컷 프롬프트로 웹 Gemini 앱에서 만들어 관리 화면에서 올린다 (기본 방식)
- [NEWS_PROMPT_GPT.md](NEWS_PROMPT_GPT.md): 글과 대표 이미지를 함께 커밋. 이미지는 `content/images/` 에 두고 `coverImage: "/resources/images/posts/파일명"` 으로 연결한다

두 경우 모두 `coverPrompt` 는 글 댓글 맨 위에 DevMonster(작성자) 댓글로 달린다.

`content/samples/`는 화면 확인용 가상 글입니다. `mvn clean package -Psample` 빌드에만 들어가며, 실제 글·날짜·기사 근거로 사용하지 않습니다.

## 형식

```markdown
---
title: "글 제목"
slug: "english-slug"
category: "news"
publishedAt: "2026-10-13"
summary: "목록에 표시할 한두 문장"
coverPrompt: "대표 이미지 생성 프롬프트 (이미지가 아직 없을 때)"
coverImage: "/resources/images/대표-이미지.png"
coverAlt: "대표 이미지에 실제로 보이는 내용"
hashtags: ["IT뉴스", "DevGear", "DevMonster"]
sources:
  - publisher: "매체명"
    title: "기사 원제"
    url: "https://..."
    publishedAt: "2026-10-12"
---

## 첫 번째 소제목

본문입니다. **중요한 내용**과 *강조할 문장*을 쓸 수 있습니다.

> Dev Monster의 시선: 인용(>)으로 쓰면 강조 박스로 표시됩니다.
```

위 내용은 **형식 예시**입니다.

## 필드 규칙

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `title`, `summary` | 예 | 글자 |
| `slug` | 예 | 영문 소문자·숫자·하이픈(`my-post-1`). 주소 `/blog/posts/{slug}` 가 되며 다른 글과 겹치면 안 된다 |
| `category` | 예 | `development`, `news`, `gear` 중 하나 |
| `publishedAt` | 예 | `"yyyy-MM-dd"`, `Asia/Seoul` 기준. **따옴표 필수**(없으면 YAML 이 날짜 객체로 읽어 오류) |
| `coverImage` | 아니요 | AI 가 함께 커밋한 이미지는 `/resources/images/posts/파일명`(파일은 `content/images/`). 가져올 때 파일이 없거나 이미지가 아니면 표지 없이 등록되고 서버 로그에 경고가 남는다. 관리 화면에서 올린 이미지는 `/uploads/...` |
| `coverAlt`, `coverCaption` | 아니요 | `coverImage` 가 있으면 `coverAlt` 필수. 이미지 없이 `coverAlt` 초안만 미리 넣어 둘 수도 있다(관리 화면에서 이미지를 올릴 때 설명 칸에 들어가 있음) |
| `coverPrompt` | 아니요 | 가져올 때 이 글에 "DevMonster" 의 "대표 이미지 프롬프트" 댓글로 한 번 등록되고, 댓글 목록 맨 위에 보인다 |
| `draft` | 아니요 | `true` 면 임시저장으로 가져온다(관리자만 보임). 없으면 바로 발행 |
| `hashtags` | 아니요 | 글자 목록. 앞의 `#` 는 빼도 되고 붙여도 된다 |
| `sources` | 아니요 | `publisher`·`title` 필수, `url` 은 `https://` 로 시작, `publishedAt` 은 따옴표 날짜. 글 아래 "참고한 기사와 자료" 로 표시 |

IT 뉴스 글에는 실제 기사별 매체명, 원제, 보도일, URL을 확인해 `sources`에 남깁니다.

## 본문 규칙

- `##` 는 목차에 나오는 소제목, `###` 는 작은 소제목입니다. 소제목에 이모지를 넣으면 목차에도 그대로 나옵니다.
- 문단, `**굵게**`, `*기울임*`, `>` 인용, 목록(`-`), 코드 블록(```)을 쓸 수 있습니다. 번호 목록(`1.`)도 점 목록으로 표시됩니다.
- 원본 HTML, 이미지(`![]()`), 구분선(`---`)은 화면에 나오지 않습니다. 표는 지원하지 않아 `|` 가 섞인 문단으로 보이므로 쓰지 않습니다.
- 본문 링크는 글자만 남으므로 기사 URL은 `sources` 에 씁니다.
- **굵게 주의:** 닫는 `**` 바로 앞이 `)`·`"` 같은 문장부호이고 바로 뒤에 한글이 붙으면 굵게가 적용되지 않습니다(CommonMark 규칙).
  `**Mistral(미스트랄)**은` 대신 `**Mistral**(미스트랄)은` 처럼 씁니다. `mvn test` 가 남은 `**` 를 찾아 실패시킵니다.

## 검사와 반영

`mvn test`(또는 `mvn package`)가 `content/posts`와 `content/samples`의 모든 글을 해석해 보고, 필수 필드·형식·남은 `**` 를 검사합니다. 실패하면 파일명과 이유가 출력됩니다. 새 파일은 WAR를 다시 빌드·배포해 앱이 시작될 때 들어옵니다.

이미지 흐름: AI가 `coverPrompt`만 넣어 글을 커밋하면, 배포 후 그 글의 댓글에 프롬프트가 달립니다. 이미지를 만든 뒤 관리 화면의 글 수정에서 "이미지 올리기"로 올리고 이미지 설명을 써서 저장합니다.
