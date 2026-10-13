-- blog_app 으로 두 DB 에 각각 실행:
--   psql -h 127.0.0.1 -U blog_app -d blog         -f db/001_schema.sql
--   psql -h 127.0.0.1 -U blog_app -d blog_preview -f db/001_schema.sql

-- 글. 관리 화면에서 쓰거나, content/posts/*.md 를 앱 시작 시 가져온다(slug 가 없을 때만)
CREATE TABLE blog_post (
    id            BIGSERIAL PRIMARY KEY,
    slug          VARCHAR(120)  NOT NULL UNIQUE,         -- 주소 /posts/{slug}. 지운 글의 slug 도 남겨 다시 가져오지 않는다
    title         VARCHAR(200)  NOT NULL,
    category      VARCHAR(20)   NOT NULL CHECK (category IN ('development', 'news', 'gear')),
    summary       VARCHAR(300)  NOT NULL,
    body          TEXT          NOT NULL,                -- Markdown 원문. 화면에 그릴 때 블록으로 바꾼다
    cover_image   VARCHAR(300),                          -- /resources/images/... 또는 /uploads/...
    cover_alt     VARCHAR(200),
    cover_caption VARCHAR(200),
    cover_prompt  VARCHAR(2000),                         -- 대표 이미지 프롬프트. 댓글로 한 번 등록된다
    hashtags      VARCHAR(500),                          -- 쉼표로 구분
    sources       TEXT,                                  -- 출처 JSON 배열 [{publisher,title,url,publishedAt}]
    status        VARCHAR(10)   NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    published_at  DATE          NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMP                              -- 삭제는 행을 지우지 않고 시각만 남긴다
);
CREATE INDEX idx_blog_post_list ON blog_post (status, published_at DESC, id DESC) WHERE deleted_at IS NULL;

CREATE TABLE blog_comment (
    id            BIGSERIAL PRIMARY KEY,
    post_id       BIGINT        NOT NULL REFERENCES blog_post(id),
    author_name   VARCHAR(20)   NOT NULL,
    password_hash VARCHAR(100),                          -- BCrypt. 프롬프트 댓글은 NULL(관리자만 삭제)
    content       VARCHAR(2100) NOT NULL,                -- 방문자 1000자 제한은 서비스에서. 프롬프트 댓글은 더 길 수 있다
    is_prompt     BOOLEAN       NOT NULL DEFAULT FALSE,  -- cover_prompt 로 자동 등록한 작성자 댓글
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMP,
    CHECK (is_prompt OR password_hash IS NOT NULL)       -- 방문자 댓글은 비밀번호 필수
);
CREATE INDEX idx_blog_comment_post ON blog_comment (post_id, created_at);
-- 프롬프트 댓글은 글마다 1개. 매퍼의 ON CONFLICT (post_id) WHERE is_prompt 와 짝이다
CREATE UNIQUE INDEX uq_blog_comment_prompt ON blog_comment (post_id) WHERE is_prompt;
