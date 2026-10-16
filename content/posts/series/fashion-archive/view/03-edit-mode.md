---
title: "상세 화면의 읽기 모드와 편집 모드"
slug: "fashion-archive-view-edit-mode"
category: "development"
publishedAt: "2026-10-08"
summary: "같은 상세 화면을 읽기와 편집으로 나눠 쓰는 data-editing 속성과 CSS, 편집 버튼의 aria-pressed, 저장 후 다시 불러와 그리는 방식, 같은 아이템 링크를 연달아 넣는 입력 흐름, 화면 아래 알림(토스트)을 설명합니다."
series: "fashion-archive"
chapter: "view"
seriesOrder: 3
---
## 이 글에서 할 일

사진 상세 화면은 평소에는 사진과 제품 링크를 **읽는 화면**이고, "편집"을 누르면 같은 자리에서 링크를 고치고 지우고 더하는 **편집 화면**이 됩니다(커밋 `43824d6` "reading/editing mode on detail"). 이 글에서는 한 화면이 두 모드를 오가는 방법을 봅니다.

<!-- 📷 스크린샷: 상세 화면 읽기 모드와 편집 모드 -->

## 모드는 속성 하나

`WEB-INF/jsp/fashion/photo/detail.jsp`

```jsp
<%--
  모델: photoId. 데이터는 detail.js가 POST /api/photos/detail 로 가져와 그린다. 로그인 여부는 레이아웃의 isAdmin(JS 전역)
  관리자도 기본은 읽기 화면. "편집"을 누르면 data-editing="true"가 되어 .edit-only 요소와 링크 수정 폼이 나타난다.
--%>
<article class="photo-detail" id="photoDetail" data-id="${photoId}" data-editing="false" hidden>
    ...
    <c:if test="${not empty sessionScope.loginId}">
        <button type="button" class="text-btn edit-toggle" id="editToggle" aria-pressed="false">편집</button>
    </c:if>
    ...
    <c:if test="${not empty sessionScope.loginId}">
        <p class="admin-actions edit-only">
            <a class="text-btn" href="<c:url value='/admin/photos/${photoId}/edit'/>">제목·메모·카테고리 수정</a>
            <button type="button" class="text-btn is-danger" id="deletePhotoBtn">사진 삭제</button>
        </p>
    </c:if>
```

`resources/css/fashion.css`

```css
[data-editing="false"] .edit-only { display: none; }
```

- 화면 전체(`<article>`)에 `data-editing` 속성 하나를 둡니다.
- 편집할 때만 보일 요소에는 `edit-only` 클래스를 붙입니다.
- CSS 한 줄이 `data-editing="false"`일 때 `edit-only`를 모두 숨깁니다.

요소마다 보이고 숨기는 JS를 쓰지 않고, **속성 하나만 바꾸면 CSS가 나머지를 처리합니다.**

## 편집 버튼

`resources/js/app/photo/detail.js`

```js
toggleEditing = function(){
    settings.editing = !settings.editing;
    m$.article.dataset.editing = String(settings.editing);
    m$.editToggle.textContent = settings.editing ? '편집 끝내기' : '편집';
    m$.editToggle.setAttribute('aria-pressed', String(settings.editing));
    renderLinkGroups(settings.groups);
},
```

| 줄 | 하는 일 |
|---|---|
| `dataset.editing` | `data-editing` 속성을 바꿔 CSS가 `edit-only`를 보이거나 숨김 |
| `textContent` | 버튼 글자를 "편집" ↔ "편집 끝내기" |
| `aria-pressed` | 화면 낭독기에 이 버튼이 **눌린 상태인지** 알림. 켜고 끄는 버튼의 표준 표시 |
| `renderLinkGroups(settings.groups)` | 마지막으로 받은 링크 데이터로 목록을 다시 그림. 읽기면 링크, 편집이면 수정 폼 |

모드를 바꿀 때 서버를 다시 부르지 않습니다. 마지막 응답을 `settings.groups`에 보관해 두었다가 그대로 다시 그립니다.

```js
links.forEach(function(link){
    var li = document.createElement('li');
    li.appendChild(settings.editing ? linkEditForm(link) : linkRow(link));
    ul.appendChild(li);
});
```

같은 데이터로 읽기용(`linkRow`)과 편집용(`linkEditForm`) 중 하나를 만듭니다.

편집 버튼은 로그인했을 때만 JSP가 그립니다. JS는 버튼이 있을 때만 이벤트를 연결합니다.

```js
if(m$.editToggle){
    m$.editToggle.addEventListener('click', toggleEditing);
}
```

## 저장하면 다시 불러오기

```js
/* 성공하면 다시 불러와 서버가 정한 그룹 순서를 그대로 쓴다 */
send = function(target, data, successMessage, afterSuccess){
    if(settings.submitting) return;
    settings.submitting = true;
    App.post(target, data)
        .then(function(res){
            if(res.code !== '00'){
                App.error('실패', res.message);
                return;
            }
            App.toast(successMessage);
            if(afterSuccess) afterSuccess();
            load();
        })
        .catch(function(){})
        .then(function(){ settings.submitting = false; });
},
```

링크를 추가, 수정, 삭제한 뒤 화면의 데이터를 JS가 직접 고치지 않고 **상세 API를 다시 불러** 그립니다. 아이템 이름을 바꾸면 묶음이 바뀌는데, 묶는 규칙은 서버(`groupLinks`)에 있으므로 서버의 결과를 그대로 쓰는 것이 맞습니다. 편집 모드는 유지되므로 계속 고칠 수 있습니다.

`settings.submitting`은 저장 버튼을 빠르게 두 번 눌러 같은 요청이 두 번 가는 것을 막습니다. 마지막 `.then`은 성공과 실패 어느 쪽이든 실행되어 다시 누를 수 있게 풀어 줍니다.

## 같은 아이템 링크를 연달아 넣기

```js
/* 같은 아이템에 여러 링크를 연달아 넣는 흐름: 아이템 이름은 남기고 나머지만 비운다 */
addLink = function(){
    var label = m$.addForm.elements.itemLabel.value;
    send(url.addLink, App.formToObject(m$.addForm), '링크를 추가했어요.', function(){
        m$.addForm.reset();
        m$.addForm.elements.itemLabel.value = label;
        m$.addForm.elements.url.focus();
    });
},
```

"아우터"에 비슷한 제품 세 개를 넣을 때, 추가할 때마다 아이템 이름을 다시 적지 않아도 됩니다. 폼을 비우되 아이템 이름은 남기고, 초점을 주소 칸으로 옮겨 바로 다음 주소를 붙여 넣을 수 있게 합니다.

## 토스트 알림

`resources/js/common/common.js`

```js
// App.toast(message, type): 화면 하단에 잠깐 떠 있다 사라지는 알림. type: 'info' | 'error'
App.toast = function (message, type) {
    var el = document.getElementById('appToast');
    if (!el) {
        el = document.createElement('div');
        el.id = 'appToast';
        el.className = 'toast';
        el.setAttribute('role', 'status');
        el.setAttribute('aria-live', 'polite');
        document.body.appendChild(el);
    }
    clearTimeout(el._timer);
    el.textContent = message;
    el.className = 'toast is-visible' + (type === 'error' ? ' is-error' : '');
    el._timer = setTimeout(function () { el.className = 'toast'; }, 3200);
};
```

| 부분 | 뜻 |
|---|---|
| 요소를 한 번만 만들어 재사용 | 알림이 여러 개 쌓이지 않음 |
| `role="status"`, `aria-live="polite"` | 화면 낭독기가 하던 말을 끝낸 뒤 알림 내용을 읽어 줌 |
| `clearTimeout` | 알림이 떠 있는 동안 새 알림이 오면 사라지는 시간을 다시 3.2초로 |
| `textContent` | 서버가 보낸 문장도 글자로만 넣음 |

저장 성공은 "저장했어요." 같은 짧은 알림이, 실패는 서버의 `message`가 빨간 알림(`is-error`)으로 나옵니다. 확인 버튼을 누를 필요 없이 사라지므로 편집 흐름을 끊지 않습니다. 사진과 링크 **삭제**처럼 되돌릴 수 없는 작업만 브라우저의 `confirm()`으로 한 번 묻습니다.

## 정리

- `data-editing` 속성 하나와 CSS 한 줄로 편집 전용 요소를 보이고 숨깁니다.
- 편집 버튼은 `aria-pressed`로 상태를 알리고, 보관해 둔 데이터로 읽기용이나 편집용 목록을 다시 그립니다.
- 저장 후에는 상세 API를 다시 불러 서버가 묶은 결과를 그대로 그립니다.
- 같은 아이템 링크를 연달아 넣도록 아이템 이름을 남기고, 결과는 토스트로 짧게 알립니다.
