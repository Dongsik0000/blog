<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@ include file="/common/taglib.jsp"%>
<%-- 글 쓰기·수정: postEdit.js 가 data-post-id 가 있으면 /admin/post/detail 로 불러오고, /admin/post/save 로 저장한다 --%>
<t:layout title="${empty postId ? '새 글 쓰기' : '글 수정'}" page="admin">
    <jsp:attribute name="script">
        <script defer src="<c:url value='/resources/js/common/common.js'/>"></script>
        <script defer src="<c:url value='/resources/js/app/admin/postEdit.js'/>"></script>
    </jsp:attribute>
    <jsp:body>
        <form class="post-editor" id="postForm" data-post-id="<c:out value='${postId}'/>" novalidate>
            <header class="admin-head">
                <div>
                    <a class="admin-back" href="<c:url value='/admin/posts'/>"><span aria-hidden="true">←</span> 글 관리</a>
                    <h1 id="editorTitle"><c:out value="${empty postId ? '새 글 쓰기' : '글 수정'}"/><span>.</span></h1>
                    <p class="editor-state" id="editorState">아직 저장하지 않았습니다.</p>
                </div>
                <div class="admin-actions">
                    <a class="button" id="viewLink" href="#" target="_blank" rel="noopener" hidden>글 보기</a>
                    <button class="button" id="saveDraft" type="button" data-status="DRAFT">임시저장</button>
                    <button class="button primary" id="savePublish" type="button" data-status="PUBLISHED">발행</button>
                </div>
            </header>
            <p class="form-status" id="editorStatus" role="status"></p>

            <div class="editor-grid">
                <div class="editor-main">
                    <div class="field">
                        <label for="postTitle">제목</label>
                        <input class="editor-title" id="postTitle" name="title" maxlength="200" required>
                    </div>
                    <div class="field">
                        <label for="postSummary">요약</label>
                        <textarea id="postSummary" name="summary" rows="2" maxlength="300" required aria-describedby="postSummaryHelp"></textarea>
                        <small id="postSummaryHelp">목록과 글 맨 위에 보이는 한두 문장입니다. 300자까지.</small>
                    </div>
                    <div class="field">
                        <div class="field-head">
                            <label for="postBody">본문 (Markdown)</label>
                            <%-- 파일 칸은 숨기고 라벨을 버튼처럼 보인다(대표 이미지와 같은 방식). 고른 이미지는 커서 위치에 들어간다 --%>
                            <input class="sr-only" id="bodyImageFile" type="file" accept="image/jpeg,image/png,image/webp,image/gif">
                            <label class="button" for="bodyImageFile">본문에 이미지 넣기</label>
                        </div>
                        <textarea class="editor-body" id="postBody" name="body" rows="24" required aria-describedby="postBodyHelp"></textarea>
                        <details class="editor-help" id="postBodyHelp">
                            <summary>본문 쓰는 법</summary>
                            <ul>
                                <li><code>## 소제목</code> 목차에 나오는 소제목, <code>### 작은 소제목</code></li>
                                <li><code>**굵게**</code>, <code>*기울임*</code>. 닫는 ** 바로 앞에 괄호·따옴표를 두고 한글을 붙이면 굵게가 안 됩니다: <code>**이름**(설명)은</code> 처럼 씁니다</li>
                                <li><code>&gt; 인용</code> 강조 박스. "Dev Monster의 시선" 같은 문단에 씁니다</li>
                                <li><code>- 목록</code>, <code>1. 번호 목록</code>, 코드 글자는 <code>`코드`</code>, 코드 묶음은 <code>```</code> 세 개로 감쌉니다</li>
                                <li>표: <code>| 칸 | 칸 |</code> 다음 줄에 <code>|---|---|</code></li>
                                <li>링크: <code>[글자](https://주소)</code>, 블로그 다른 글은 <code>[글자](/posts/주소)</code></li>
                                <li>이미지: "본문에 이미지 넣기"를 누르면 커서 자리에 <code>![설명](주소)</code>가 들어갑니다. 대괄호 안 설명을 실제 그림에 맞게 고칩니다</li>
                                <li><code>&lt;!-- 메모 --&gt;</code>는 방문자에게 보이지 않습니다(예: 아직 넣지 않은 스크린샷 자리). HTML 은 화면에 나오지 않습니다</li>
                            </ul>
                        </details>
                    </div>
                </div>

                <aside class="editor-side" aria-label="발행 정보">
                    <section class="editor-panel" aria-labelledby="panel-publish">
                        <h2 id="panel-publish">발행 정보</h2>
                        <div class="field">
                            <label for="postCategory">주제</label>
                            <select id="postCategory" name="category" required>
                                <option value="news">IT 뉴스</option>
                                <option value="development">개발 기록</option>
                                <option value="gear">IT 장비</option>
                            </select>
                        </div>
                        <div class="field">
                            <label for="postPublishedAt">발행일</label>
                            <input id="postPublishedAt" type="date" name="publishedAt" required>
                        </div>
                        <div class="field">
                            <label for="postSeries">시리즈 (선택)</label>
                            <select id="postSeries" name="series">
                                <option value="">시리즈 아님</option>
                                <c:forEach var="s" items="${seriesList}"><option value="<c:out value='${s.slug}'/>"><c:out value="${s.title}"/></option></c:forEach>
                            </select>
                        </div>
                        <div class="series-fields" id="seriesFields" hidden>
                            <div class="field">
                                <label for="postChapter">장</label>
                                <%-- 모든 시리즈의 장을 두고, postEdit.js 가 고른 시리즈의 장만 보인다 --%>
                                <select id="postChapter" name="chapter">
                                    <c:forEach var="s" items="${seriesList}"><c:forEach var="ch" items="${s.chapters}" varStatus="st"><option value="<c:out value='${ch.id}'/>" data-series="<c:out value='${s.slug}'/>"><c:out value="${st.count}"/>. <c:out value="${ch.title}"/></option></c:forEach></c:forEach>
                                </select>
                            </div>
                            <div class="field">
                                <label for="postSeriesOrder">장 안 순서</label>
                                <input id="postSeriesOrder" name="seriesOrder" type="number" min="1" max="999" inputmode="numeric">
                            </div>
                        </div>
                        <div class="field">
                            <label for="postSlug">주소</label>
                            <div class="input-prefix"><span aria-hidden="true">/posts/</span><input id="postSlug" name="slug" maxlength="120" pattern="[a-z0-9]+(-[a-z0-9]+)*" autocapitalize="off" spellcheck="false" aria-describedby="postSlugHelp"></div>
                            <small id="postSlugHelp">영문 소문자·숫자·하이픈. 비워 두면 발행일로 자동으로 만듭니다.</small>
                        </div>
                    </section>

                    <section class="editor-panel" aria-labelledby="panel-cover">
                        <h2 id="panel-cover">대표 이미지</h2>
                        <input type="hidden" id="postCoverImage" name="coverImage">
                        <figure class="cover-preview" id="coverPreview" hidden><img id="coverPreviewImage" src="" alt=""></figure>
                        <div class="cover-actions">
                            <%-- 파일 칸은 숨기고 라벨을 버튼처럼 보인다. 칸이 앞에 있어야 CSS 로 키보드 포커스를 라벨에 표시할 수 있다 --%>
                            <input class="sr-only" id="coverFile" type="file" accept="image/jpeg,image/png,image/webp,image/gif">
                            <label class="button" for="coverFile">이미지 올리기</label>
                            <button class="button" id="coverRemove" type="button" hidden>이미지 빼기</button>
                        </div>
                        <small>JPG, PNG, WEBP, GIF. 가로 1600px보다 크거나 5MB를 넘으면 자동으로 줄여서 올립니다</small>
                        <div class="field">
                            <label for="postCoverAlt">이미지 설명 (대체 텍스트)</label>
                            <input id="postCoverAlt" name="coverAlt" maxlength="200" aria-describedby="postCoverAltHelp">
                            <small id="postCoverAltHelp">이미지에 실제로 보이는 것을 적습니다. 이미지를 넣으면 필수입니다.</small>
                        </div>
                        <div class="field">
                            <label for="postCoverCaption">캡션 (선택)</label>
                            <input id="postCoverCaption" name="coverCaption" maxlength="200">
                        </div>
                        <div class="field">
                            <label for="postCoverPrompt">이미지 프롬프트 (선택)</label>
                            <textarea id="postCoverPrompt" name="coverPrompt" rows="3" maxlength="2000" aria-describedby="postCoverPromptHelp"></textarea>
                            <small id="postCoverPromptHelp">저장하면 이 글에 DevMonster 댓글로 한 번 달립니다.</small>
                        </div>
                    </section>

                    <section class="editor-panel" aria-labelledby="panel-tags">
                        <h2 id="panel-tags">태그와 출처</h2>
                        <div class="field">
                            <label for="postHashtags">해시태그</label>
                            <input id="postHashtags" name="hashtags" aria-describedby="postHashtagsHelp">
                            <small id="postHashtagsHelp">쉼표나 띄어쓰기로 구분합니다. 예: IT뉴스, DevGear</small>
                        </div>
                        <div class="field">
                            <label for="postSources">출처</label>
                            <textarea id="postSources" name="sources" rows="4" aria-describedby="postSourcesHelp"></textarea>
                            <small id="postSourcesHelp">한 줄에 하나: 매체명 | 기사 제목 | https://주소 | 보도일(yyyy-mm-dd)</small>
                        </div>
                    </section>

                    <section class="editor-panel editor-danger" id="dangerPanel" aria-labelledby="panel-danger" hidden>
                        <h2 id="panel-danger">글 삭제</h2>
                        <p>삭제한 글은 목록과 주소에서 사라지고, 같은 주소는 다시 쓸 수 없습니다.</p>
                        <button class="button danger" id="deletePost" type="button">이 글 삭제</button>
                    </section>
                </aside>
            </div>
        </form>
    </jsp:body>
</t:layout>
