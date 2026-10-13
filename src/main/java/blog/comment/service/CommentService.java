package blog.comment.service;

import blog.cmmn.util.Response;

import java.util.Map;

// 댓글 업무 로직. 입력 오류·비밀번호 불일치 같은 결과도 여기서 판단해 Response 로 돌려준다.
// throws Exception: ApiController 의 catch 블록이 컴파일되려면 필요하다
public interface CommentService {

    // param: slug. admin 이면 임시저장 글의 댓글도 본다
    Response selectCommentList(Map<String, Object> param, boolean admin) throws Exception;

    // param: slug, authorName, password, content, website(봇 방지 숨은 칸)
    Response saveComment(Map<String, Object> param, String clientIp) throws Exception;

    // param: commentId, password. admin 이면 비밀번호 없이 지운다
    Response deleteComment(Map<String, Object> param, String clientIp, boolean admin) throws Exception;
}
