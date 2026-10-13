package blog.cmmn.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

// 관리자 세션. 방문자에게는 세션을 만들지 않는다(getSession(false)).
public class SessionUtil {

    private static final int ADMIN_SESSION_SECONDS = 4 * 60 * 60; // 글을 오래 쓰는 동안 끊기지 않게 4시간

    // 정적 메서드만 쓰는 클래스
    private SessionUtil() {
    }

    // 관리자로 로그인한 요청인지
    public static boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(Constants.SESSION_ADMIN));
    }

    // 로그인: 기존 세션을 버리고 새로 만든다(세션 고정 공격 방지)
    public static void loginAdmin(HttpServletRequest request) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(Constants.SESSION_ADMIN, Boolean.TRUE);
        session.setMaxInactiveInterval(ADMIN_SESSION_SECONDS);
    }

    public static void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
