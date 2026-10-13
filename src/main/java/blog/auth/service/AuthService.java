package blog.auth.service;

import blog.cmmn.util.Response;

import java.util.Map;

// 관리자 로그인 판단. 세션을 만드는 일은 컨트롤러가 한다(웹 계층의 일)
public interface AuthService {

    // param: password. 맞으면 SUCCESS
    Response checkAdminPassword(Map<String, Object> param, String clientIp) throws Exception;
}
