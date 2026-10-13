package blog.interceptor;

import blog.cmmn.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

// /comment/**, /auth/**, /admin/** 의 POST 는 Ajax(JSON 또는 이미지 업로드) 요청만 받는다 - 다른 사이트의 폼 전송(CSRF) 차단.
// 다른 출처에서 X-Requested-With 헤더를 붙이려면 브라우저가 CORS 사전 요청을 하는데, 서버는 CORS 를 허용하지 않는다.
public class ApiRequestInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String contentType = request.getContentType() == null ? "" : request.getContentType().toLowerCase(Locale.ROOT);
        boolean allowedBody = contentType.startsWith("application/json") || contentType.startsWith("multipart/form-data");
        if (RequestUtil.isAjax(request) && allowedBody) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"99\",\"message\":\"허용되지 않는 요청입니다.\"}");
        return false;
    }
}
