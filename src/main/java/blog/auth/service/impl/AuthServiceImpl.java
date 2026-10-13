package blog.auth.service.impl;

import blog.auth.service.AuthService;
import blog.cmmn.util.AttemptLimiter;
import blog.cmmn.util.Constants;
import blog.cmmn.util.Response;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

// 관리자 비밀번호 확인. 관리자는 한 명이라 계정 테이블 없이 /etc/blog/app.env 의 BLOG_ADMIN_PASSWORD 와 비교한다
@Service("authService")
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Value("${Globals.Blog.AdminPassword}")
    private String adminPassword;

    // 같은 IP 로 5분에 5번까지 (비밀번호 무작위 대입 방지)
    private final AttemptLimiter loginLimiter = new AttemptLimiter(5, 5 * 60 * 1000L);

    @PostConstruct
    void warnIfDisabled() {
        if (adminPassword == null || adminPassword.isEmpty()) {
            logger.warn("BLOG_ADMIN_PASSWORD 가 비어 있어 관리자 로그인을 할 수 없습니다.");
        }
    }

    @Override
    public Response checkAdminPassword(Map<String, Object> param, String clientIp) throws Exception {
        if (!loginLimiter.tryAcquire(clientIp)) {
            return Response.of(Constants.LIMITED, "로그인을 너무 많이 시도했습니다. 5분 뒤에 다시 시도해 주세요.", null);
        }
        String input = param.get("password") == null ? "" : String.valueOf(param.get("password"));
        // equals 대신 MessageDigest.isEqual: 응답 시간 차이로 비밀번호가 새어 나가지 않게
        boolean matched = adminPassword != null && !adminPassword.isEmpty()
                && MessageDigest.isEqual(adminPassword.getBytes(StandardCharsets.UTF_8), input.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            return Response.of(Constants.PASSWORD_MISMATCH, "비밀번호가 맞지 않습니다.", null);
        }
        return Response.of(Constants.SUCCESS);
    }
}
