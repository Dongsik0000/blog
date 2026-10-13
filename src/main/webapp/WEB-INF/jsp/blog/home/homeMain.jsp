<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<t:layout title="개발과 기술의 기록" page="home">
    <div class="blog-layout home-layout">
        <section class="feed" aria-labelledby="home-title">
            <header class="feed-heading">
                <h1 id="home-title">요즘, 이런 기술을 봅니다<span>.</span></h1>
                <p>만들면서 배운 것부터 오늘 눈에 들어온 소식까지.</p>
            </header>
            <t:categoryNav/>
            <div class="feed-caption"><span>최근 글</span><a href="<c:url value='/posts'/>">모든 글 보기 <span aria-hidden="true">→</span></a></div>
            <t:postList items="${recentPosts}" featured="true" welcome="true"/>
        </section>
        <t:blogSidebar categories="false"/>
    </div>
</t:layout>
