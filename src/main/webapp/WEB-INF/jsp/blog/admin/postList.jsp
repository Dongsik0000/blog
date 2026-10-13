<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<%-- 글 관리 목록: adminPostList.js 가 /admin/post/list 로 채운다. 임시저장 글 포함 --%>
<t:layout title="글 관리" page="admin">
    <jsp:attribute name="script">
        <script defer src="<c:url value='/resources/js/common/common.js'/>"></script>
        <script defer src="<c:url value='/resources/js/app/admin/adminPostList.js'/>"></script>
    </jsp:attribute>
    <jsp:body>
        <section class="admin-page" aria-labelledby="admin-title">
            <header class="admin-head">
                <div>
                    <h1 id="admin-title">글 관리<span>.</span></h1>
                    <p id="adminPostCount">글을 불러오는 중입니다.</p>
                </div>
                <div class="admin-actions">
                    <button class="button" id="logoutButton" type="button">로그아웃</button>
                    <a class="button primary" href="<c:url value='/admin/posts/new'/>">새 글 쓰기</a>
                </div>
            </header>
            <div class="category-nav admin-filter" role="group" aria-label="상태로 보기">
                <button type="button" class="is-active" data-status="" aria-pressed="true">전체</button>
                <button type="button" data-status="PUBLISHED" aria-pressed="false">발행</button>
                <button type="button" data-status="DRAFT" aria-pressed="false">임시저장</button>
            </div>
            <p class="form-status" id="adminPostStatus" role="status"></p>
            <ul class="admin-post-list" id="adminPostList"></ul>
        </section>
    </jsp:body>
</t:layout>
