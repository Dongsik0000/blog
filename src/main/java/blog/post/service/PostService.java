package blog.post.service;

import blog.cmmn.util.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// 글. 원본은 DB(blog_post). content/posts/*.md 는 앱 시작 시 DB 에 없는 글만 가져온다.
public interface PostService {

    // ── 공개 화면 (JSP) ──

    // 발행된 글 최신순 limit 개
    List<Map<String, Object>> selectRecentPosts(int limit);

    // 발행된 글 한 페이지: {posts, currentPage, totalPages, selectedCategory}. category 가 틀리면 전체
    Map<String, Object> selectPostPage(String category, int page);

    // 글 하나 + 본문 블록. includeDraft 면 임시저장 글도. 없으면 null
    Map<String, Object> selectPost(String slug, boolean includeDraft);

    // 댓글이 쓰는 글 id. 없으면 null
    Long selectPostId(String slug, boolean includeDraft);

    // ── 관리 화면 (Ajax) ──

    Response selectAdminPostList() throws Exception;

    // param: id
    Response selectAdminPost(Map<String, Object> param) throws Exception;

    // param: id(없으면 새 글), title, slug, category, publishedAt, summary, body, status,
    //        coverImage, coverAlt, coverCaption, coverPrompt, hashtags(쉼표·공백 구분), sources(한 줄에 하나)
    Response savePost(Map<String, Object> param) throws Exception;

    // param: id
    Response deletePost(Map<String, Object> param) throws Exception;

    Response uploadImage(MultipartFile file) throws Exception;
}
