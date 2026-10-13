package blog.interceptor;

import blog.cmmn.util.Constants;
import blog.cmmn.util.RequestUtil;
import blog.cmmn.util.SessionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// 모든 요청: 관리자 여부를 request 속성 isAdmin 에 넣는다(JSP 가 수정 링크·관리 메뉴를 보일 때 쓴다).
// /admin/** 는 로그인해야 통과. 로그인 화면(/admin/login)만 예외. Ajax 는 JSON, 화면은 로그인 화면으로 보낸다.
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        boolean admin = SessionUtil.isAdmin(request);
        request.setAttribute("isAdmin", admin);

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (admin || !path.startsWith("/admin/") || path.equals("/admin/login")) {
            return true;
        }

        if (RequestUtil.isAjax(request)) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"" + Constants.UNAUTHORIZED + "\",\"message\":\"로그인이 필요합니다. 다른 탭에서 다시 로그인한 뒤 저장해 주세요.\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/admin/login");
        }
        return false;
    }
}
