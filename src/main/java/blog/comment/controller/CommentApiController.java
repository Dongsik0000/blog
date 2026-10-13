package blog.comment.controller;

import blog.cmmn.util.Constants;
import blog.cmmn.util.RequestUtil;
import blog.cmmn.util.Response;
import blog.cmmn.util.SessionUtil;
import blog.comment.service.CommentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;

// 댓글 Ajax API (comment.js). 검증·판단은 CommentServiceImpl, 여기서는 예외만 Response 로 바꾼다.
// 사용자에게는 내부 오류 내용을 보이지 않고 로그에만 남긴다.
@Controller
public class CommentApiController {

    private static final Logger logger = LoggerFactory.getLogger(CommentApiController.class);

    @Resource(name = "commentService")
    private CommentService commentService;

    @ResponseBody
    @PostMapping("comment/list")
    public Response list(@RequestBody HashMap<String, Object> param, HttpServletRequest request) throws Exception {
        Response result = null;

        try {
            result = commentService.selectCommentList(param, SessionUtil.isAdmin(request));
        } catch (NullPointerException npe) {
            logger.error("댓글 목록 조회 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "댓글을 불러오는 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("댓글 목록 조회 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "댓글을 불러오는 중 오류가 발생했습니다.", null);
        } catch (DataAccessException dae) {
            logger.error("댓글 목록 조회 중 DB 오류가 발생했습니다.", dae);
            result = Response.of(Constants.FAIL, "댓글을 불러오는 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("댓글 목록 조회 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "댓글을 불러오는 중 오류가 발생했습니다.", null);
        }

        return result;
    }

    @ResponseBody
    @PostMapping("comment/save")
    public Response save(@RequestBody HashMap<String, Object> param, HttpServletRequest request) throws Exception {
        Response result = null;

        try {
            result = commentService.saveComment(param, RequestUtil.clientIp(request));
        } catch (NullPointerException npe) {
            logger.error("댓글 등록 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "댓글 등록 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("댓글 등록 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "댓글 등록 중 오류가 발생했습니다.", null);
        } catch (DataAccessException dae) {
            logger.error("댓글 등록 중 DB 오류가 발생했습니다.", dae);
            result = Response.of(Constants.FAIL, "댓글 등록 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("댓글 등록 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "댓글 등록 중 오류가 발생했습니다.", null);
        }

        return result;
    }

    @ResponseBody
    @PostMapping("comment/delete")
    public Response delete(@RequestBody HashMap<String, Object> param, HttpServletRequest request) throws Exception {
        Response result = null;

        try {
            result = commentService.deleteComment(param, RequestUtil.clientIp(request), SessionUtil.isAdmin(request));
        } catch (NullPointerException npe) {
            logger.error("댓글 삭제 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "댓글 삭제 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("댓글 삭제 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "댓글 삭제 중 오류가 발생했습니다.", null);
        } catch (DataAccessException dae) {
            logger.error("댓글 삭제 중 DB 오류가 발생했습니다.", dae);
            result = Response.of(Constants.FAIL, "댓글 삭제 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("댓글 삭제 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "댓글 삭제 중 오류가 발생했습니다.", null);
        }

        return result;
    }
}
