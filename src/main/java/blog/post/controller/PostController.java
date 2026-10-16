package blog.post.controller;

import blog.post.service.PostService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// 공개 화면: 글을 Model 에 담고 JSP 경로만 반환한다.
// 글은 검색엔진과 JS 가 꺼진 브라우저도 읽어야 하므로 Ajax 가 아니라 JSP 에서 그린다 (댓글만 Ajax).
// 반환 경로 앞에 '/' 를 붙이지 않는다: viewResolver prefix 가 "/WEB-INF/jsp/blog/" 라서 "//" 가 된다.
// @PathVariable·@RequestParam 은 이름을 꼭 적는다: Spring 6.1 부터 컴파일 옵션(-parameters) 없이는 변수 이름을 알 수 없다.
@Controller
public class PostController {

    private static final int RECENT_POST_COUNT = 6;

    @Resource(name = "postService")
    private PostService postService;

    @RequestMapping(value = {"/", "home"}, method = {RequestMethod.GET, RequestMethod.POST})
    public String home(Model model) {
        model.addAttribute("recentPosts", postService.selectRecentPosts(RECENT_POST_COUNT));
        return "home/homeMain";
    }

    // ?category=news&page=2. 틀린 값은 전체·첫 페이지로
    @RequestMapping(value = "posts", method = {RequestMethod.GET, RequestMethod.POST})
    public String posts(@RequestParam(value = "category", required = false) String category,
                        @RequestParam(value = "page", required = false) String page, Model model) {
        model.addAllAttributes(postService.selectPostPage(category, parsePage(page)));
        return "post/postMain";
    }

    // 임시저장 글은 관리자에게만 보인다. 없는 글은 404 → web.xml error-page 가 error.jsp 를 보여 준다
    @RequestMapping(value = "posts/{slug}", method = {RequestMethod.GET, RequestMethod.POST})
    public String detail(@PathVariable("slug") String slug, HttpServletRequest request, Model model) {
        Map<String, Object> post = postService.selectPost(slug, Boolean.TRUE.equals(request.getAttribute("isAdmin")));
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("post", post);
        return "post/postDetail";
    }

    // 시리즈 화면: 장별 목차. 임시저장 글은 관리자에게만 보인다
    @RequestMapping(value = "series/{slug}", method = {RequestMethod.GET, RequestMethod.POST})
    public String series(@PathVariable("slug") String slug, HttpServletRequest request, Model model) {
        Map<String, Object> series = postService.selectSeries(slug, Boolean.TRUE.equals(request.getAttribute("isAdmin")));
        if (series == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("series", series);
        return "post/seriesMain";
    }

    private static int parsePage(String page) {
        try {
            return page == null ? 1 : Integer.parseInt(page);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
