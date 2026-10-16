package blog.post.dao;

import jakarta.annotation.Resource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

// 글 SQL 호출. 매퍼: sqlmap/mappers/blog/post/post.xml
@Repository("postDAO")
public class PostDAO {

    @Resource(name = "sqlSession-blog-postgre")
    private SqlSessionTemplate sqlSession;

    public static final String SQL_PATH = "blog.post.dao.PostDAO";

    // 발행된 글 목록 (category, limit, offset)
    public List<Map<String, Object>> selectPublishedList(Map<String, Object> param) {
        return sqlSession.selectList(SQL_PATH + ".selectPublishedList", param);
    }

    // 발행된 글 수 (category)
    public int selectPublishedCount(Map<String, Object> param) {
        return sqlSession.selectOne(SQL_PATH + ".selectPublishedCount", param);
    }

    // slug 로 글 하나 (slug, includeDraft)
    public Map<String, Object> selectPostBySlug(Map<String, Object> param) {
        return sqlSession.selectOne(SQL_PATH + ".selectPostBySlug", param);
    }

    // 시리즈의 글 (series, includeDraft). 순서는 서비스가 장 순서로 다시 정렬한다
    public List<Map<String, Object>> selectSeriesPosts(Map<String, Object> param) {
        return sqlSession.selectList(SQL_PATH + ".selectSeriesPosts", param);
    }

    // 시리즈별 발행된 글 수 [{series, postCount}]
    public List<Map<String, Object>> selectSeriesCounts() {
        return sqlSession.selectList(SQL_PATH + ".selectSeriesCounts");
    }

    // 관리 화면 목록
    public List<Map<String, Object>> selectAdminPostList() {
        return sqlSession.selectList(SQL_PATH + ".selectAdminPostList");
    }

    // id 로 글 하나 (편집 화면)
    public Map<String, Object> selectPostById(Map<String, Object> param) {
        return sqlSession.selectOne(SQL_PATH + ".selectPostById", param);
    }

    // slug 사용 수 (slug, excludeId)
    public int selectSlugCount(Map<String, Object> param) {
        return sqlSession.selectOne(SQL_PATH + ".selectSlugCount", param);
    }

    // 새 글. 등록 후 param 의 id 가 채워진다
    public int insertPost(Map<String, Object> param) {
        return sqlSession.insert(SQL_PATH + ".insertPost", param);
    }

    // Markdown 가져오기. 같은 slug 가 있으면 0 (id 가 채워지지 않는다)
    public int insertImportedPost(Map<String, Object> param) {
        return sqlSession.insert(SQL_PATH + ".insertImportedPost", param);
    }

    public int updatePost(Map<String, Object> param) {
        return sqlSession.update(SQL_PATH + ".updatePost", param);
    }

    public int deletePost(Map<String, Object> param) {
        return sqlSession.update(SQL_PATH + ".deletePost", param);
    }
}
