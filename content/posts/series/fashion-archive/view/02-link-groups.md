---
title: "아이템 이름별로 제품 링크 묶기"
slug: "fashion-archive-view-link-groups"
category: "development"
publishedAt: "2026-10-08"
summary: "서버가 LinkedHashMap으로 링크를 아이템 이름별로 묶어 등록 순서를 유지하는 groupLinks, 화면이 묶음 이름을 제목으로 그리는 방법, 링크 주소에서 사이트 이름을 보여 주는 표시와 새 탭으로 여는 안전한 링크를 설명합니다."
series: "fashion-archive"
chapter: "view"
seriesOrder: 2
---
## 이 글에서 할 일

사진 상세 화면의 제품 링크는 "아우터", "바지"처럼 **아이템 이름별로 묶어** 보여 줍니다. DB에는 링크가 한 줄씩 있을 뿐이고 묶음은 없습니다. 이 글에서는 서버가 묶고 화면이 그리는 과정을 봅니다.

## DB: 한 줄에 링크 하나

```sql
SELECT id, photo_id, item_label, url, title, note, sort_order
FROM product_link
WHERE photo_id = #{photoId}
ORDER BY sort_order, id
```

예를 들어 사진 하나에 링크가 이렇게 있다고 해 봅시다.

| id | item_label | title |
|---|---|---|
| 31 | 아우터 | 린넨 자켓 |
| 32 | 바지 | 와이드 슬랙스 |
| 33 | 아우터 | 크롭 블레이저 |

등록 순서(`sort_order`)로 가져옵니다. "아우터" 링크가 중간에 끼어 있어도 순서대로 나옵니다.

## 서버: LinkedHashMap으로 묶기

`PhotoApiController`

```java
// 링크를 item_label별로 묶는다. 라벨이 처음 나온 순서(= 등록 순서)를 유지하기 위해 LinkedHashMap
public static Map<String, List<Map<String, Object>>> groupLinks(List<Map<String, Object>> links) {
    Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (Map<String, Object> link : links) {
        String label = String.valueOf(link.get("item_label"));
        if (!groups.containsKey(label)) {
            groups.put(label, new ArrayList<Map<String, Object>>());
        }
        groups.get(label).add(link);
    }
    return groups;
}
```

결과는 다음과 같습니다.

```json
{
  "아우터": [ {"id": 31, "title": "린넨 자켓", ...}, {"id": 33, "title": "크롭 블레이저", ...} ],
  "바지":   [ {"id": 32, "title": "와이드 슬랙스", ...} ]
}
```

### HashMap이 아니라 LinkedHashMap

| Map | 키의 순서 |
|---|---|
| `HashMap` | 정해져 있지 않음. "바지"가 먼저 나올 수도 있음 |
| `LinkedHashMap` | **넣은 순서** 그대로 |

라벨이 처음 나온 순서(가장 먼저 등록한 아이템이 위)를 지키려고 `LinkedHashMap`을 씁니다. Jackson은 Map을 JSON으로 바꿀 때 Map이 주는 순서대로 쓰므로, 이 순서가 화면까지 전달됩니다. 화면 JS 주석도 이 점을 적어 둡니다.

```js
/* linkGroups: { "아우터": [link, ...], "바지": [...] } — 서버가 LinkedHashMap이라 순서가 유지된다 */
```

이 메서드는 DB 없이 목록만 받아 결과를 돌려주므로 `PhotoApiControllerTest`로 순서와 묶음을 검사합니다([테스트](/posts/fashion-archive-test-tests)).

## 화면: 묶음 이름을 제목으로

`resources/js/app/photo/detail.js` (일부)

```js
renderLinkGroups = function(groups){
    var labels = Object.keys(groups);
    m$.groups.textContent = '';
    m$.labels.textContent = '';
    m$.linkEmpty.hidden = labels.length > 0 || settings.editing;

    labels.forEach(function(label){
        var links = groups[label],
            h2 = document.createElement('h2'),
            small = document.createElement('small'),
            ul = document.createElement('ul'),
            option = document.createElement('option');

        h2.className = 'link-label';
        h2.textContent = label + ' ';
        small.textContent = '비슷한 제품 ' + links.length;
        h2.appendChild(small);
        ...
        option.value = label;
        m$.labels.appendChild(option);
        ...
    });
},
```

- 묶음마다 아이템 이름을 제목(`<h2>`)으로, 그 아래 링크 목록(`<ul>`)을 그립니다. "아우터 비슷한 제품 2"처럼 개수도 붙입니다.
- `Object.keys`는 JSON에 적힌 순서대로 키를 돌려주므로 서버의 순서를 따릅니다.

![가을 코디 사진 옆에 아우터 제품 링크 두 개와 신발 제품 링크 한 개가 묶음별로 놓인 상세 화면](/uploads/capture-fashion-04-detail-read.jpg)

### 아이템 이름 제안: datalist

같은 묶음에 링크를 더할 때는 아이템 이름을 똑같이 적어야 같은 묶음이 됩니다. "아우터"와 "아우터 "처럼 조금만 달라도 다른 묶음이 됩니다. 그래서 지금 있는 아이템 이름을 `<datalist>`의 선택지로 넣습니다.

```js
option.value = label;
m$.labels.appendChild(option);
```

```js
label.setAttribute('list', 'labelOptions');
```

입력 칸에 `list="labelOptions"`를 연결하면 브라우저가 기존 이름을 자동 완성 목록으로 보여 줍니다. 별도 라이브러리 없이 HTML 기능만으로 입력 실수를 줄입니다. 서버도 아이템 이름의 앞뒤 공백을 지우고 저장합니다(`Validation.requireText`).

## 링크 한 줄: 사이트 이름 보여 주기

```js
/* 읽기용: 제목 + 도메인, 그 아래 메모(왜 이 제품인지). 화살표 대신 어디로 가는지를 보여준다 */
linkRow = function(link){
    ...
    a.href = link.url;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    name.textContent = link.title || host;
    ...
},

hostOf = function(value){
    try {
        return new URL(value).hostname.replace(/^www\./, '');
    } catch(e){
        return value;
    }
},
```

| 처리 | 이유 |
|---|---|
| 제품 이름 옆에 도메인(`musinsa.com` 등) | 누르기 전에 어느 쇼핑몰로 가는지 알 수 있음 |
| 제품 이름이 없으면 도메인을 이름으로 | 빈 링크가 생기지 않음 |
| `target="_blank"` | 쇼핑몰은 새 탭에서 열고 이 화면은 그대로 둠 |
| `rel="noopener noreferrer"` | 새 탭의 페이지가 `window.opener`로 이 화면을 조작하지 못하게 하고, 이전 주소(Referer)도 보내지 않음 |

링크마다 적은 "왜 이 제품인지" 메모는 링크 아래 작은 글씨로 보여 줍니다(커밋 `85f1783` "per-link note").

## 정리

- DB는 링크를 한 줄씩 등록 순서로 돌려주고, 서버가 `LinkedHashMap`으로 아이템 이름별로 묶어 순서를 지킵니다.
- 화면은 묶음 이름을 제목으로, 링크를 목록으로 그리고, 기존 이름을 `<datalist>`로 제안합니다.
- 링크에는 도메인을 함께 보여 주고, `noopener noreferrer`를 붙여 새 탭으로 엽니다.
