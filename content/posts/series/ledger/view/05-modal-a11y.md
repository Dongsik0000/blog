---
title: "공통 알림 창과 접근성"
slug: "ledger-view-modal-a11y"
category: "development"
publishedAt: "2026-10-08"
summary: "알림, 오류, 확인 창을 _alert, _error, _confirm 함수 하나씩으로 만들고, 키보드와 화면 낭독기 사용자도 쓸 수 있게 초점, Esc, aria 속성, 본문 바로가기, 키보드 순서 변경을 처리한 방법을 설명합니다."
series: "ledger"
chapter: "view"
seriesOrder: 5
---
## 이 글에서 할 일

브라우저 기본 `alert()`, `confirm()`은 모양을 바꿀 수 없고 화면 흐름을 멈춥니다. Ledger는 직접 만든 알림 창 세 개를 씁니다. 그리고 마우스 없이 키보드로만 쓰거나 화면 낭독기로 듣는 사용자도 쓸 수 있게 **접근성**을 챙겼습니다.

## 세 가지 창

`resources/js/common/modal.js`

| 함수 | 용도 | 예 |
|---|---|---|
| `_alert(title, desc, callback, options)` | 완료 알림. `autoClose`면 잠깐 보였다 사라짐 | "저장했어요" |
| `_error(title, desc, callback)` | 오류나 확인 요청 | "확인해 주세요" |
| `_confirm(title, desc, callback)` | 확인과 취소. 확인을 누르면 `callback` 실행 | "작성 중인 내용을 버릴까요?" |

```js
_confirm('작성 중인 내용을 버릴까요?', '저장하지 않은 입력은 사라져요.', function () {
    discard();
    dialog.close();
});
```

창 요소는 처음 부를 때 한 번 만들고 다시 씁니다. 제목과 설명은 `textContent`로 넣어 HTML로 해석되지 않습니다.

## 접근성 1. 화면 낭독기에게 "이것은 대화 상자"라고 알리기

```js
'<section class="manage-modal-dialog" role="dialog" aria-modal="true" aria-labelledby="commonConfirmTitle">'
```

| 속성 | 뜻 |
|---|---|
| `role="dialog"` | 이 영역은 대화 상자 |
| `aria-modal="true"` | 창이 열린 동안 뒤 화면은 쓸 수 없음 |
| `aria-labelledby` | 창의 제목이 어느 요소인지. 낭독기가 창을 열 때 제목을 읽음 |
| `aria-hidden` | 닫혀 있을 때 `true`로 낭독기가 무시하게 |

아이콘처럼 뜻 없는 장식에는 `aria-hidden="true"`를 붙여 낭독기가 읽지 않게 합니다.

## 접근성 2. 초점(포커스) 관리

키보드 사용자는 **초점이 있는 곳**만 조작할 수 있습니다. 창이 열렸는데 초점이 뒤 화면에 남아 있으면 창을 닫을 방법이 없습니다.

```js
modal._returnFocus = document.activeElement && document.activeElement !== document.body ? document.activeElement : null;
...
modal.querySelector('.common-confirm-ok').focus();
```

- 창을 열 때 **지금 초점이 있던 곳을 기억**하고, 확인 버튼으로 초점을 옮깁니다.
- 취소하면 기억해 둔 곳(예: 입력하던 칸)으로 초점을 **되돌립니다.** 사용자가 하던 일로 바로 돌아갈 수 있습니다.

```js
// 취소하면 확인창을 열기 전에 초점이 있던 곳(입력 칸 등)으로 돌아간다
let back = modal._returnFocus;
if(back && back.isConnected && typeof back.focus === 'function') back.focus();
```

`isConnected`는 그 요소가 아직 화면에 있는지 확인합니다. 그사이 목록을 다시 그려 요소가 사라졌다면 초점을 옮기지 않습니다.

## 접근성 3. 키보드로 닫기

```js
modal.addEventListener('keydown', function(e){
    if(e.key === 'Escape'){
        // 취소
    }else if(e.key === 'Enter' && e.target === modal){
        // 버튼에 초점이 있으면 그 버튼의 기본 동작(취소면 취소)을 따른다. 창 자체에 초점이 있을 때만 확인
        modal.querySelector('.common-confirm-ok').click();
    }
});
```

- **Esc**는 항상 취소입니다.
- **Enter**는 초점이 있는 버튼을 따릅니다. 처음에는 Enter가 무조건 확인이었는데, 사용자가 Tab으로 "취소"에 초점을 옮기고 Enter를 눌렀는데 확인이 되는 문제가 있어 리뷰에서 고쳤습니다(커밋 "리뷰 반영: 확인창 Enter 는 초점 버튼을 따름").

## 편집 창 위에 알림 띄우기

거래 편집 창은 브라우저 기본 `<dialog>`를 `showModal()`로 엽니다. 이렇게 연 창은 브라우저의 **최상위 레이어**에 올라가서, `z-index`를 아무리 크게 줘도 `body`에 붙은 알림 창이 그 뒤에 가려집니다.

```js
// 편집 창(<dialog>.showModal())은 브라우저 최상위 레이어에 올라가서, body 에 붙은 모달은 z-index 와 상관없이 그 뒤에 가려진다.
// 열린 dialog 가 있으면 모달을 그 안으로 옮겨 같은 레이어에서 보이게 한다.
function _modalHost(modal){
    var host = document.querySelector('dialog[open]') || document.body;
    if (modal.parentNode !== host) host.appendChild(modal);
}
```

알림 창을 열 때마다 열린 `<dialog>`가 있는지 보고, 있으면 그 **안으로 옮겨** 같은 레이어에서 보이게 합니다.

## 그 밖의 접근성 장치

| 장치 | 코드 | 누구를 위해 |
|---|---|---|
| 본문 바로가기 | `<a class="skip-link" href="#main-content">본문으로 바로가기</a>` | 키보드 사용자가 매번 메뉴를 Tab으로 지나가지 않게 |
| 초점 테두리 | `:focus-visible { outline: 3px solid #8baf74; }` | 키보드로 이동할 때 지금 위치가 보이게 |
| 현재 메뉴 | `aria-current="page"` | 낭독기가 "현재 페이지"라고 읽음 |
| 숨긴 제목 | `<legend class="sr-only">거래 입력</legend>`, `<caption class="sr-only">` | 눈에는 안 보이지만 낭독기는 읽음 |
| 진행 안내 | `role="status" aria-live="polite"` | 검색 결과 같은 변화를 낭독기가 알려 줌 |
| 움직임 줄이기 | `@media (prefers-reduced-motion: reduce)` | 운영체제에서 애니메이션을 줄인 사용자 |
| 순서 변경 | 끌어서 옮기기 + 손잡이에서 **↑, ↓ 키** | 마우스를 쓰기 어려운 사용자 |

카테고리 순서 변경은 마우스로 끌어 옮길 수 있지만, 그것만 있으면 키보드 사용자는 순서를 바꿀 수 없습니다. 손잡이에 초점을 두고 화살표 키로도 옮길 수 있게 했습니다.

```js
if(!handle || (e.key !== 'ArrowUp' && e.key !== 'ArrowDown')) return;
...
list.insertBefore(item, e.key === 'ArrowUp' ? target : target.nextSibling);
```

## 정리

- 알림, 오류, 확인 창을 `_alert`, `_error`, `_confirm` 함수로 공통화했습니다.
- `role="dialog"`, `aria-modal`, `aria-labelledby`로 낭독기에 창을 알립니다.
- 열 때 초점을 창으로 옮기고, 취소하면 원래 자리로 되돌립니다. Esc는 취소, Enter는 초점 버튼을 따릅니다.
- `<dialog>` 위에 알림이 보이도록 열린 dialog 안으로 옮깁니다.
- 본문 바로가기, 초점 테두리, 키보드 순서 변경 같은 장치로 마우스 없이도 쓸 수 있게 했습니다.
