package blog.auth.controller;

import blog.auth.service.AuthService;
import blog.cmmn.util.Constants;
import blog.cmmn.util.RequestUtil;
import blog.cmmn.util.Response;
import blog.cmmn.util.SessionUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;

// 관리자 로그인·로그아웃 API (login.js, admin 화면의 로그아웃 버튼)
@Controller
public class AuthApiController {

    private static final Logger logger = LoggerFactory.getLogger(AuthApiController.class);

    @Resource(name = "authService")
    private AuthService authService;

    @ResponseBody
    @PostMapping("auth/login")
    public Response login(@RequestBody HashMap<String, Object> param, HttpServletRequest request) throws Exception {
        Response result = null;

        try {
            result = authService.checkAdminPassword(param, RequestUtil.clientIp(request));
            if (Constants.SUCCESS.equals(result.getCode())) {
                SessionUtil.loginAdmin(request);
            }
        } catch (NullPointerException npe) {
            logger.error("관리자 로그인 중 NullPointerException이 발생했습니다.", npe);
            result = Response.of(Constants.FAIL, "로그인 중 오류가 발생했습니다.", null);
        } catch (IllegalArgumentException iae) {
            logger.error("관리자 로그인 중 IllegalArgumentException이 발생했습니다.", iae);
            result = Response.of(Constants.FAIL, "로그인 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("관리자 로그인 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "로그인 중 오류가 발생했습니다.", null);
        }

        return result;
    }

    @ResponseBody
    @PostMapping("auth/logout")
    public Response logout(HttpServletRequest request) throws Exception {
        Response result = null;

        try {
            SessionUtil.logout(request);
            result = Response.of(Constants.SUCCESS);
        } catch (IllegalStateException ise) {
            logger.error("관리자 로그아웃 중 IllegalStateException이 발생했습니다.", ise);
            result = Response.of(Constants.FAIL, "로그아웃 중 오류가 발생했습니다.", null);
        } catch (Exception e) {
            logger.error("관리자 로그아웃 중 알 수 없는 오류가 발생했습니다.", e);
            result = Response.of(Constants.FAIL, "로그아웃 중 오류가 발생했습니다.", null);
        }

        return result;
    }
}
