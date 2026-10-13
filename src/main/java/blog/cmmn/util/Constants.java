package blog.cmmn.util;

// 결과 코드. JS 의 App.CODE(common.js)와 같은 값을 유지한다.
public class Constants {

    public static final String SUCCESS = "00"; // 공통 응답 성공
    public static final String FAIL    = "99"; // 공통 응답 실패

    public static final String INVALID           = "90"; // 입력 오류 (message 를 화면에 그대로 보인다)
    public static final String NOT_FOUND         = "91"; // 글·댓글 없음 (이미 지운 댓글 포함)
    public static final String PASSWORD_MISMATCH = "92"; // 댓글 삭제 비밀번호 불일치
    public static final String LIMITED           = "93"; // 짧은 시간에 너무 많이 시도함
    public static final String DUPLICATE         = "94"; // 이미 쓰인 글 주소(slug)
    public static final String UNAUTHORIZED      = "95"; // 관리자 로그인이 필요함(세션 만료 포함)

    // 블로그 작성자 이름. 프롬프트 댓글의 작성자이고, 방문자는 이 이름으로 댓글을 쓸 수 없다
    public static final String AUTHOR_NAME = "DevMonster";

    // 세션 키: 관리자 로그인 여부(Boolean)
    public static final String SESSION_ADMIN = "blogAdmin";
}
