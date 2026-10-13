package blog.comment.dao;

import jakarta.annotation.Resource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

// 댓글 SQL 호출. 매퍼: sqlmap/mappers/blog/comment/comment.xml
@Repository("commentDAO")
public class CommentDAO {

    @Resource(name = "sqlSession-blog-postgre")
    private SqlSessionTemplate sqlSession;

    public static final String SQL_PATH = "blog.comment.dao.CommentDAO";

    // 글의 댓글 목록
    public List<Map<String, Object>> selectCommentList(Map<String, Object> param) {
        return sqlSession.selectList(SQL_PATH + ".selectCommentList", param);
    }

    // 방문자 댓글 등록
    public int insertComment(Map<String, Object> param) {
        return sqlSession.insert(SQL_PATH + ".insertComment", param);
    }

    // 대표 이미지 프롬프트 댓글 등록 (이미 있으면 0)
    public int insertPromptComment(Map<String, Object> param) {
        return sqlSession.insert(SQL_PATH + ".insertPromptComment", param);
    }

    // 삭제할 댓글의 비밀번호 해시. 없거나 이미 지웠으면 null
    public Map<String, Object> selectCommentForDelete(Map<String, Object> param) {
        return sqlSession.selectOne(SQL_PATH + ".selectCommentForDelete", param);
    }

    // 댓글 삭제(soft delete)
    public int deleteComment(Map<String, Object> param) {
        return sqlSession.update(SQL_PATH + ".deleteComment", param);
    }
}
