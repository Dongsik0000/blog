<%@ tag language="java" pageEncoding="UTF-8" body-content="empty"%>
<%@ attribute name="slug" required="true"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<%-- 글 본문 아래 댓글 영역. 목록은 comment.js 가 /comment/list 로 받아 #commentList 에 그린다.
     폼은 hidden 으로 두고 JS 가 켜질 때만 보인다 (JS 없이 제출되면 비밀번호가 처리되지 않은 채 오가므로) --%>
<section class="comment-section" id="comments" data-slug="<c:out value='${slug}'/>" data-admin="${requestScope.isAdmin eq true}" aria-labelledby="comments-title">
    <h2 id="comments-title">댓글 <span class="comment-count" id="commentCount"></span></h2>
    <ul class="comment-list" id="commentList"></ul>
    <p class="comment-status" id="commentStatus" role="status"></p>
    <noscript><p class="comment-status">댓글은 JavaScript를 켜야 볼 수 있습니다.</p></noscript>
    <form class="comment-form" id="commentForm" method="post" hidden>
        <div class="comment-fields">
            <div class="field">
                <label for="commentAuthor">이름</label>
                <input id="commentAuthor" name="authorName" maxlength="20" required autocomplete="nickname">
            </div>
            <div class="field">
                <label for="commentPassword">비밀번호</label>
                <input id="commentPassword" type="password" name="password" minlength="4" required autocomplete="new-password" aria-describedby="commentPasswordHelp">
                <small id="commentPasswordHelp">댓글을 지울 때 필요합니다.</small>
            </div>
        </div>
        <div class="field">
            <label for="commentContent">댓글</label>
            <textarea id="commentContent" name="content" rows="4" maxlength="1000" required></textarea>
        </div>
        <%-- 봇 방지: 사람에게는 보이지 않는 칸. 값이 들어오면 서버가 거절한다 --%>
        <div class="comment-trap" aria-hidden="true">
            <label for="commentWebsite">웹사이트</label>
            <input id="commentWebsite" name="website" tabindex="-1" autocomplete="off">
        </div>
        <button class="button primary" id="commentSubmit" type="submit">댓글 등록</button>
    </form>
</section>
