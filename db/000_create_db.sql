-- postgres 슈퍼유저로 1회 실행:  sudo -u postgres psql -f db/000_create_db.sql
-- 비밀번호는 실행할 때 입력받는다(파일·셸 기록에 남지 않게). /etc/blog/app.env 의 BLOG_DB_PASSWORD 와 같게 넣는다.
\prompt 'blog_app 비밀번호: ' blog_password
CREATE ROLE blog_app LOGIN PASSWORD :'blog_password';
CREATE DATABASE blog OWNER blog_app ENCODING 'UTF8';
-- 미리보기(-Psample) 전용. 테스트 댓글이 운영 DB 에 남지 않게 분리한다
CREATE DATABASE blog_preview OWNER blog_app ENCODING 'UTF8';
