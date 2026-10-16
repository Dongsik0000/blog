-- 시리즈(개발 기록 묶음). blog_app 으로 두 DB 에 각각 실행. **새 코드를 배포하기 전에** 실행한다
--   psql -h 127.0.0.1 -U blog_app -d blog         -f db/002_series.sql
--   psql -h 127.0.0.1 -U blog_app -d blog_preview -f db/002_series.sql
-- 시리즈와 장의 이름·순서는 content/series.yml 에 있고, 글은 그 slug·장 id 와 장 안 순서만 가진다.

ALTER TABLE blog_post
    ADD COLUMN series       VARCHAR(50),   -- content/series.yml 의 slug. 시리즈가 아닌 글은 NULL
    ADD COLUMN chapter      VARCHAR(50),   -- 그 시리즈의 장 id
    ADD COLUMN series_order INT,           -- 장 안에서 읽는 순서(1부터)
    ADD CONSTRAINT blog_post_series_fields CHECK (
        (series IS NULL AND chapter IS NULL AND series_order IS NULL)
        OR (series IS NOT NULL AND chapter IS NOT NULL AND series_order BETWEEN 1 AND 999)
    );
CREATE INDEX idx_blog_post_series ON blog_post (series, chapter, series_order) WHERE deleted_at IS NULL;
