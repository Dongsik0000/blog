<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<%-- 관리자 로그인: login.js 가 /auth/login 으로 확인하고, 성공하면 글 관리로 이동한다 --%>
<t:layout title="관리자 로그인" page="admin">
    <jsp:attribute name="script">
        <script defer src="<c:url value='/resources/js/common/common.js'/>"></script>
        <script defer src="<c:url value='/resources/js/app/auth/login.js'/>"></script>
    </jsp:attribute>
    <jsp:body>
        <section class="admin-login" aria-labelledby="login-title">
            <h1 id="login-title">관리자 로그인<span>.</span></h1>
            <p>글을 쓰고 고치려면 관리자 비밀번호를 입력해 주세요.</p>
            <form class="admin-login-form" id="loginForm" hidden>
                <div class="field">
                    <label for="loginPassword">비밀번호</label>
                    <input id="loginPassword" type="password" name="password" required autocomplete="current-password">
                </div>
                <p class="form-status" id="loginStatus" role="status"></p>
                <button class="button primary" id="loginSubmit" type="submit">로그인</button>
            </form>
            <noscript><p class="form-status">로그인하려면 JavaScript를 켜 주세요.</p></noscript>
        </section>
    </jsp:body>
</t:layout>
