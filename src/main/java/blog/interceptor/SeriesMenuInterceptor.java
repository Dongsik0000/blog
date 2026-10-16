package blog.interceptor;

import blog.post.service.PostService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// 모든 화면의 사이드바가 시리즈 목록을 보이도록 request 속성 seriesMenu 에 넣는다.
// 컨트롤러를 거치지 않는 화면(/about view-controller)도 인터셉터는 거치므로 여기서 넣는다.
@Component("seriesMenuInterceptor")
public class SeriesMenuInterceptor implements HandlerInterceptor {

    @Resource(name = "postService")
    private PostService postService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("seriesMenu", postService.selectSeriesList());
        return true;
    }
}
