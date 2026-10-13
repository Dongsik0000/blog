<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<t:layout title="DevGear 소개" page="about">
    <div class="blog-layout">
        <article class="about-page">
            <header class="feed-heading"><h1>안녕하세요, DevMonster입니다<span>.</span></h1><p>만들며 배우는 개발자의 개인 블로그, DevGear.</p></header>
            <div class="about-letter">
                <p class="about-lead">잘 몰랐던 것이 조금씩 이해되는 순간.<br>그 순간들을 여기에 남기려고 합니다.</p>
                <p>DevGear는 신입 웹개발자가 직접 겪은 개발 경험과 관심 있게 읽은 IT 뉴스, 작업에 쓰는 장비에 관해 이야기하는 공간입니다.</p>
                <p>문제를 해결한 과정도, 한 번에 풀리지 않았던 이유도 기록합니다. 기술 소식을 읽을 때는 새로운 기능만큼 개발자의 일에 어떤 의미가 있는지도 생각해 봅니다.</p>
                <h2>이곳에서 나누는 이야기</h2>
                <dl class="about-topics">
                    <div><dt>개발 기록</dt><dd>웹 개발을 공부하고 직접 구현하며 배운 것들. 시행착오와 해결 과정을 함께 남깁니다.</dd></div>
                    <div><dt>IT 뉴스</dt><dd>AI, 반도체, 보안, 플랫폼의 주요 소식. 기사에서 확인한 사실과 제 생각을 구분해 전합니다.</dd></div>
                    <div><dt>IT 장비</dt><dd>개발자의 작업 환경에 닿는 하드웨어와 도구. 관심 있는 제품과 직접 사용한 경험을 구분해 이야기합니다.</dd></div>
                </dl>
                <p>비슷한 문제를 만난 분에게는 작은 힌트가, 기술이 궁금한 분에게는 편하게 읽을 이야기가 되면 좋겠습니다.</p>
                <p class="about-signature" translate="no">DevMonster</p>
            </div>
            <a class="text-link" href="<c:url value='/posts'/>">모든 글 보기 <span aria-hidden="true">→</span></a>
        </article>
        <t:blogSidebar/>
    </div>
</t:layout>
