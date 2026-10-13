# ⚙️ DevGear — 개발하며 배운 것과 IT 소식을 기록하는 블로그

> 만들다 막히고, 읽다가 궁금해진 것들.

신입 웹개발자 DevMonster 가 직접 만들어 운영하는 블로그입니다. 네이버 블로그에 손으로 옮겨 올리던 글을, 직접 만든 블로그에서 쓰고 AI 가 쓴 글도 자동으로 받도록 바꿨습니다.

| 네이버 블로그에서의 불편 | DevGear 에서는 |
|---|---|
| AI 가 써 준 글을 매번 복사해 붙여 넣어야 했다 | AI 가 GitHub 에 Markdown 글을 커밋하면 **배포될 때 자동으로 등록**됩니다 |
| 대표 이미지 프롬프트를 글과 따로 챙겨야 했다 | 글에 적은 프롬프트가 **그 글의 댓글로 달리고**, 이미지를 만든 뒤 관리 화면에서 올리면 끝납니다 |

PC 와 휴대폰 브라우저에서 같은 화면으로 읽고 씁니다.

---

## 주요 기능

**읽기**
- 홈: 최신 글 6개. 가장 최근 글은 표지와 제목을 크게
- 모든 글: 개발 기록 · IT 뉴스 · IT 장비 주제별 보기, 10개씩 페이지 나누기
- 글 본문: 소제목으로 만든 목차, 인용 강조 박스, 참고한 기사 목록, 해시태그

**댓글**
- 로그인 없이 이름과 비밀번호로 남기고, 같은 비밀번호로 지웁니다
- 같은 IP 에서 짧은 시간에 너무 많이 쓰거나 지우려 하면 잠시 막고, 봇이 채우는 숨은 칸으로 자동 등록을 거릅니다

**글 관리 (관리자)**
- 관리자 비밀번호로 로그인하면 상단에 `관리` 메뉴, 글마다 `수정` 링크가 생깁니다
- 새 글 쓰기 · 수정: 제목, 요약, Markdown 본문, 주제, 발행일, 주소, 대표 이미지 업로드, 이미지 프롬프트, 해시태그, 출처
- 임시저장 · 발행 · 발행 취소 · 삭제. 임시저장 글은 관리자에게만 보입니다
- Ctrl+S 로 저장, 저장하지 않고 나가려 하면 먼저 묻습니다
- 댓글을 비밀번호 없이 지울 수 있습니다

**AI 자동 발행 연결**
- `content/posts/*.md` 는 입력함입니다. 앱이 시작할 때 DB 에 같은 주소(slug)가 없는 글만 가져오고, 이후로는 관리 화면에서 고칩니다
- 지운 글은 주소가 남아 다시 들어오지 않습니다
- 형식이 틀린 글(필수 항목 · 날짜 · 굵게 표시 오류)은 배포 전 테스트에서 걸러집니다. 작성 규칙은 [content/README.md](content/README.md)
- 매일 IT 뉴스를 쓰는 AI 프롬프트: [Claude 용(글만)](content/NEWS_PROMPT_CLAUDE.md), [GPT 용(글 + 대표 이미지)](content/NEWS_PROMPT_GPT.md)
- 글에 적은 이미지 프롬프트는 그 글의 댓글 맨 위에 DevMonster(작성자) 댓글로 달립니다

---

## 이렇게 만들었습니다

| 영역 | 사용 기술 |
|---|---|
| 서버 | Java 17, Spring Framework 6.2 (XML 설정), MyBatis 3.5, HikariCP |
| 화면 | JSP / JSTL 3.0, 순수 JavaScript (Ajax), Pretendard |
| 글 해석 | commonmark-java (Markdown), SnakeYAML (front matter) |
| 데이터베이스 | PostgreSQL 16 |
| 실행 · 배포 | Tomcat 10.1, Docker Compose, nginx 리버스 프록시, Oracle Cloud (ARM64) |

**안전하게**
- 댓글 비밀번호는 BCrypt 로 저장하고, 관리자 로그인은 5회 실패 시 5분 차단 · 로그인 후 세션 ID 교체
- 데이터를 바꾸는 요청은 Ajax 만 받아 CSRF 를 막고, 세션 쿠키는 SameSite=Lax
- 화면에는 사용자 입력을 글자로만 넣고, 글의 Markdown 에 섞인 HTML 은 버려 XSS 를 막습니다
- 업로드는 파일 앞부분으로 형식을 확인해 JPG · PNG · WEBP · GIF 만 5MB 까지 받습니다 (SVG 제외)
- 방문자에게는 세션을 만들지 않습니다. Tomcat 과 DB 는 서버 내부에만 열고, 외부 요청은 nginx 만 받습니다
- 비밀값은 저장소에 두지 않고 서버의 `/etc/blog/app.env` 로만 넣습니다

**정확하게**
- 글 해석 · 입력 검증 · 요청 횟수 제한을 JUnit 으로 검증하고, `content/` 의 모든 글을 배포 전에 해석해 봅니다

---

## 직접 실행하기

<details>
<summary>로컬 실행</summary>

1. PostgreSQL 에 `db/` 의 SQL 을 순서대로 적용합니다. 첫 파일은 `blog_app` 비밀번호를 입력받습니다.
   ```bash
   psql -U postgres -f db/000_create_db.sql
   psql -U blog_app -d blog -f db/001_schema.sql
   ```
2. `mvn package` 로 만든 `target/blog.war` 를 Tomcat 10.1 에 컨텍스트 `/blog` 로 배포합니다.
3. http://localhost:8080/blog/ 에 접속합니다. 관리 화면은 `/blog/admin/login` 입니다.

설정은 환경변수로 바꿉니다.

| 변수 | 용도 | 로컬 기본값 |
|---|---|---|
| `BLOG_DB_URL` / `BLOG_DB_USER` / `BLOG_DB_PASSWORD` | DB 연결 | `localhost:5432/blog`, `blog_app` |
| `BLOG_ADMIN_PASSWORD` | 관리자 로그인 비밀번호 | 없음 (로그인 불가) |
| `BLOG_UPLOAD_DIR` | 업로드한 대표 이미지 저장 폴더 | `/var/lib/blog/uploads` |
| `BLOG_LOG_LEVEL` | 앱 로그 수준 | `INFO` |

</details>

<details>
<summary>샘플 글로 미리보기</summary>

`content/samples/` 의 샘플 글 6개(가상의 회사 · 기사)와 이미지는 `-Psample` 빌드에만 들어가고 운영 빌드에는 들어가지 않습니다. 미리보기는 운영과 다른 DB(`blog_preview`, `000_create_db.sql` 이 함께 만든다)와 포트를 씁니다.

```bash
psql -U blog_app -d blog_preview -f db/001_schema.sql   # 처음 한 번
mvn clean package -Psample
docker build -t blog:preview .
# 운영 비밀값을 복사해 DB 주소만 blog_preview 로 바꾼다 (처음 한 번)
sudo sh -c 'sed "s#/blog\$#/blog_preview#" /etc/blog/app.env > /etc/blog/preview.env && chown root:docker /etc/blog/preview.env && chmod 640 /etc/blog/preview.env'
docker run -d --name blog-preview --network host \
  -e CATALINA_OPTS="-Dtomcat.address=127.0.0.1 -Dtomcat.port=18082" \
  --env-file /etc/blog/preview.env blog:preview
# http://127.0.0.1:18082/blog/ 확인 후
docker rm -f blog-preview
mvn clean package   # 운영 배포 전에는 샘플 없이 다시 빌드
```

</details>

<details>
<summary>서버 배포 (Docker)</summary>

```bash
sudo -u postgres psql -f db/000_create_db.sql
psql -h 127.0.0.1 -U blog_app -d blog -f db/001_schema.sql
sudo install -D -m 640 -o root -g docker deploy/app.env.example /etc/blog/app.env   # 값 입력
mvn clean package            # 테스트 포함. 실패하면 WAR 가 만들어지지 않는다
docker compose up -d --build
```

- 컨테이너는 호스트 PostgreSQL 에 붙기 위해 host 네트워크를 쓰고, Tomcat 은 `127.0.0.1:8082` 에만 열립니다.
- nginx 설정은 `deploy/nginx-blog.conf` 파일 머리말을 따릅니다. 이미지 업로드를 위해 요청 크기를 6MB 까지 받습니다.
- 업로드한 이미지는 호스트 `/var/lib/blog/uploads` 에 남습니다. DB 와 함께 백업 대상입니다.
- `/etc/blog/app.env` 를 바꾼 뒤에는 `docker compose up -d --force-recreate` 로 컨테이너를 다시 만듭니다.

</details>

---

## 화면

`design-taste-frontend` 와 `web-design-guidelines` 를 참고했습니다. 홍보용 랜딩 페이지가 아닌 개인 블로그라 제목의 위계와 읽기 밀도를 우선했습니다. 밝은 웜그레이 바탕에 강조색은 오렌지 하나, 버튼 · 입력칸은 8px, 카드 · 이미지는 16px 모서리로 맞췄습니다.

데스크톱은 글 목록과 우측 프로필의 2열, 1024px 미만에서는 글을 먼저 읽는 1열입니다. 움직임은 헤더 심볼의 회전, 메뉴 밑줄, 글 썸네일 확대와 링크 화살표의 반응에 한정하며 `prefers-reduced-motion` 을 따릅니다.

프로필 이미지 `src/main/webapp/resources/images/devmonster-avatar.png` 는 이미지 생성 도구로 만들었습니다. 생성 프롬프트는 다음과 같습니다.

```text
Use case: stylized-concept. Asset type: small author avatar for DevGear, a Korean personal web development and IT blog by DevMonster. Create a single polished 3D clay illustration of a friendly small cobalt-blue monster with two short rounded horns, simple dark eyes and understated round charcoal eyeglasses, seated behind a small unbranded silver laptop. Calm, intelligent, approachable, not childish. Centered compact bust portrait, subject fills 75% of square canvas, seamless very pale cool gray background, soft daylight studio lighting, matte tactile materials, restrained cobalt and silver palette. Clear silhouette legible at 72 pixels. No text, lettering, watermark, logos, props, gradients or UI. Square image.
```
