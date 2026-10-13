package blog.post.controller;

import blog.cmmn.util.Constants;
import blog.cmmn.util.Response;
import blog.post.service.PostService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;

// 글 관리 Ajax API (로그인 필요, AdminInterceptor). 검증·판단은 PostServiceImpl, 여기서는 예외만 Response 로 바꾼다.
@Controller
public class PostAdminApiController {

    private static final Logger logger = LoggerFactory.getLogger(PostAdminApiController.class);

    @Resource(name = "postService")
    private PostService postService;

    @ResponseBody
    @PostMapping("admin/post/list")
    public Response list() throws Exception {
        Response result = null;

        try {
            result = postService.selectAdminPostList();
        } catch (NullPointerException npe) {
            logger.error("글 목록 조회 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "글 목록을 불러오는 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("글 목록 조회 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "글 목록을 불러오는 중 오류가 발생했습니다.", null);
        } catch (DataAccessException dae) {
            logger.error("글 목록 조회 중 DB 오류가 발생했습니다.", dae);
            result = Response.of(Constants.FAIL, "글 목록을 불러오는 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("글 목록 조회 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "글 목록을 불러오는 중 오류가 발생했습니다.", null);
        }

        return result;
    }

    @ResponseBody
    @PostMapping("admin/post/detail")
    public Response detail(@RequestBody HashMap<String, Object> param) throws Exception {
        Response result = null;

        try {
            result = postService.selectAdminPost(param);
        } catch (NullPointerException npe) {
            logger.error("글 조회 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "글을 불러오는 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("글 조회 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "글을 불러오는 중 오류가 발생했습니다.", null);
        } catch (DataAccessException dae) {
            logger.error("글 조회 중 DB 오류가 발생했습니다.", dae);
            result = Response.of(Constants.FAIL, "글을 불러오는 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("글 조회 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "글을 불러오는 중 오류가 발생했습니다.", null);
        }

        return result;
    }

    @ResponseBody
    @PostMapping("admin/post/save")
    public Response save(@RequestBody HashMap<String, Object> param) throws Exception {
        Response result = null;

        try {
            result = postService.savePost(param);
        } catch (NullPointerException npe) {
            logger.error("글 저장 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "글 저장 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("글 저장 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "글 저장 중 오류가 발생했습니다.", null);
        } catch (DataAccessException dae) {
            logger.error("글 저장 중 DB 오류가 발생했습니다.", dae);
            result = Response.of(Constants.FAIL, "글 저장 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("글 저장 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "글 저장 중 오류가 발생했습니다.", null);
        }

        return result;
    }

    @ResponseBody
    @PostMapping("admin/post/delete")
    public Response delete(@RequestBody HashMap<String, Object> param) throws Exception {
        Response result = null;

        try {
            result = postService.deletePost(param);
        } catch (NullPointerException npe) {
            logger.error("글 삭제 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "글 삭제 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("글 삭제 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "글 삭제 중 오류가 발생했습니다.", null);
        } catch (DataAccessException dae) {
            logger.error("글 삭제 중 DB 오류가 발생했습니다.", dae);
            result = Response.of(Constants.FAIL, "글 삭제 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("글 삭제 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "글 삭제 중 오류가 발생했습니다.", null);
        }

        return result;
    }

    // 대표 이미지 업로드 (multipart, 필드 이름 file). 5MB 를 넘으면 컨트롤러에 오기 전에 413 으로 끝난다
    @ResponseBody
    @PostMapping("admin/image/upload")
    public Response upload(@RequestParam("file") MultipartFile file) throws Exception {
        Response result = null;

        try {
            result = postService.uploadImage(file);
        } catch (NullPointerException npe) {
            logger.error("이미지 업로드 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "이미지를 올리는 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("이미지 업로드 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "이미지를 올리는 중 오류가 발생했습니다.", null);
        } catch (IOException ioe) {
            logger.error("이미지 업로드 중 IOException이 발생했습니다. 업로드 폴더 권한을 확인하세요.", ioe);
            result = Response.of(Constants.FAIL, "이미지를 저장하지 못했습니다.", null);
        } catch (Exception e) {
            logger.error("이미지 업로드 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "이미지를 올리는 중 오류가 발생했습니다.", null);
        }

        return result;
    }
}
