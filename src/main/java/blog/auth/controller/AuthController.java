package blog.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

// 관리자 로그인 화면. 로그인 처리는 AuthApiController(login.js). 이미 로그인했으면 글 관리로 보낸다
@Controller
public class AuthController {

    @RequestMapping(value = "admin/login", method = {RequestMethod.GET, RequestMethod.POST})
    public String login(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute("isAdmin"))) {
            return "redirect:/admin/posts";
        }
        return "admin/loginMain";
    }
}
