package blog.comment.service.impl;

import blog.cmmn.util.AttemptLimiter;
import blog.cmmn.util.Constants;
import blog.cmmn.util.Response;
import blog.comment.dao.CommentDAO;
import blog.comment.service.CommentService;
import blog.post.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// 댓글 검증·저장·삭제. 대표 이미지 프롬프트 댓글은 PostServiceImpl 이 글을 저장·가져올 때 등록한다
@Service("commentService")
public class CommentServiceImpl implements CommentService {

    private static final int NAME_MAX = 20;
    private static final int CONTENT_MAX = 1000;
    private static final int PASSWORD_MIN = 4;
    private static final int PASSWORD_MAX_BYTES = 72; // BCrypt 입력 한도. 넘으면 encode/matches 가 예외를 던진다

    @Resource(name = "commentDAO")
    private CommentDAO commentDAO;

    @Resource(name = "postService")
    private PostService postService;

    @Resource(name = "passwordEncoder")
    private PasswordEncoder passwordEncoder;

    // 같은 IP 기준: 등록 10분에 5번, 삭제 시도 5분에 5번(비밀번호 무작위 대입 방지)
    private final AttemptLimiter writeLimiter = new AttemptLimiter(5, 10 * 60 * 1000L);
    private final AttemptLimiter deleteLimiter = new AttemptLimiter(5, 5 * 60 * 1000L);

    @Override
    public Response selectCommentList(Map<String, Object> param, boolean admin) throws Exception {
        Long postId = postService.selectPostId(text(param.get("slug")), admin);
        if (postId == null) {
            return Response.of(Constants.NOT_FOUND, "글을 찾을 수 없습니다.", null);
        }
        return Response.of(Constants.SUCCESS, commentDAO.selectCommentList(Map.of("postId", postId)));
    }

    @Override
    public Response saveComment(Map<String, Object> param, String clientIp) throws Exception {
        // 사람에게는 보이지 않는 칸이라 값이 있으면 봇
        if (!text(param.get("website")).isEmpty()) {
            return Response.invalid("댓글을 등록할 수 없습니다.");
        }

        String authorName = text(param.get("authorName"));
        String password = raw(param.get("password")); // 비밀번호는 공백도 그대로 쓴다
        String content = text(param.get("content"));

        if (authorName.isEmpty() || authorName.length() > NAME_MAX) {
            return Response.invalid("이름은 1~" + NAME_MAX + "자로 입력해 주세요.");
        }
        if (authorName.equalsIgnoreCase(Constants.AUTHOR_NAME)) {
            return Response.invalid("이 이름은 사용할 수 없습니다.");
        }
        if (password.length() < PASSWORD_MIN || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return Response.invalid("비밀번호는 " + PASSWORD_MIN + "자 이상으로, 너무 길지 않게 입력해 주세요.");
        }
        if (content.isEmpty() || content.length() > CONTENT_MAX) {
            return Response.invalid("댓글은 1~" + CONTENT_MAX + "자로 입력해 주세요.");
        }
        Long postId = postService.selectPostId(text(param.get("slug")), false); // 발행된 글에만 쓸 수 있다
        if (postId == null) {
            return Response.of(Constants.NOT_FOUND, "글을 찾을 수 없습니다.", null);
        }
        // 입력이 맞는 요청만 센다 (오타로 다시 보낸 것은 세지 않는다)
        if (!writeLimiter.tryAcquire(clientIp)) {
            return Response.of(Constants.LIMITED, "짧은 시간에 댓글을 너무 많이 남겼습니다. 10분 뒤에 다시 시도해 주세요.", null);
        }

        Map<String, Object> comment = new HashMap<>();
        comment.put("postId", postId);
        comment.put("authorName", authorName);
        comment.put("passwordHash", passwordEncoder.encode(password)); // 원래 비밀번호는 저장하지 않는다
        comment.put("content", content);
        commentDAO.insertComment(comment);
        return Response.of(Constants.SUCCESS);
    }

    @Override
    public Response deleteComment(Map<String, Object> param, String clientIp, boolean admin) throws Exception {
        Long commentId = id(param.get("commentId"));
        String password = raw(param.get("password"));
        if (commentId == null || (!admin && password.isEmpty())) {
            return Response.invalid("비밀번호를 입력해 주세요.");
        }
        if (!admin && !deleteLimiter.tryAcquire(clientIp)) {
            return Response.of(Constants.LIMITED, "삭제를 너무 많이 시도했습니다. 5분 뒤에 다시 시도해 주세요.", null);
        }

        Map<String, Object> target = commentDAO.selectCommentForDelete(Map.of("commentId", commentId));
        if (target == null) {
            return Response.of(Constants.NOT_FOUND, "이미 삭제된 댓글입니다.", null);
        }
        // 프롬프트 댓글은 password_hash 가 없어 관리자만 지울 수 있다
        String passwordHash = (String) target.get("passwordHash");
        boolean allowed = admin
                || (passwordHash != null
                    && password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES
                    && passwordEncoder.matches(password, passwordHash));
        if (!allowed) {
            return Response.of(Constants.PASSWORD_MISMATCH, "비밀번호가 맞지 않습니다.", null);
        }
        commentDAO.deleteComment(Map.of("commentId", commentId));
        return Response.of(Constants.SUCCESS);
    }

    // 앞뒤 공백을 뗀 글자. 없으면 ""
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    // 그대로의 글자. 없으면 ""
    private static String raw(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // JSON 숫자·글자 모두 받는다. 숫자가 아니면 null
    private static Long id(Object value) {
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
