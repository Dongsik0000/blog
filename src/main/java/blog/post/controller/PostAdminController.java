package blog.post.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

// 관리 화면(로그인 필요, AdminInterceptor). 데이터는 JS 가 PostAdminApiController 로 불러온다
@Controller
public class PostAdminController {

    // 글 관리 목록 (adminPostList.js)
    @RequestMapping(value = "admin/posts", method = {RequestMethod.GET, RequestMethod.POST})
    public String postList() {
        return "admin/postList";
    }

    // 새 글 쓰기 (postEdit.js)
    @RequestMapping(value = "admin/posts/new", method = {RequestMethod.GET, RequestMethod.POST})
    public String postNew() {
        return "admin/postEdit";
    }

    // 글 수정. postId 를 화면에 넘기면 postEdit.js 가 내용을 불러온다
    @RequestMapping(value = "admin/posts/{id}", method = {RequestMethod.GET, RequestMethod.POST})
    public String postEdit(@PathVariable("id") Long id, Model model) {
        model.addAttribute("postId", id);
        return "admin/postEdit";
    }
}
