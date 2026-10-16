---
title: "Chart.js로 통계 차트 그리기"
slug: "ledger-view-chartjs"
category: "development"
publishedAt: "2026-10-08"
summary: "요약 화면의 월별 수입과 지출 막대 차트, 카테고리 도넛 차트를 Chart.js로 그리는 방법과, 다시 그릴 때 이전 차트를 없애는 이유, 차트 옆에 표를 함께 두는 이유를 설명합니다."
series: "ledger"
chapter: "view"
seriesOrder: 6
---
## 이 글에서 할 일

요약 화면은 월별 수입과 지출을 막대로, 카테고리 비중을 도넛으로 보여 줍니다. 차트는 **Chart.js 4.4**로 그립니다. 빌드 도구 없이 파일 하나(`chart.umd.js`)만 넣으면 쓸 수 있어서, Ledger처럼 JSP와 순수 JS로 만든 화면에 잘 맞습니다.

<!-- 📷 스크린샷: 요약 화면의 막대 차트와 도넛 차트 -->

## 불러오기

`layout.tag`

```jsp
<script src="<c:url value='/resources/js/lib/chartjs/chart.umd.js'/>"></script>
```

`umd`는 `<script>` 태그로 넣으면 전역 `Chart` 객체가 생기는 형식입니다. 라이브러리 파일을 저장소에 함께 두어(외부 CDN 없이) 서버만 있으면 동작합니다.

## 차트 하나 그리기

```js
new Chart(캔버스요소, {
    type: 'bar',          // 차트 종류
    data: { ... },        // 데이터
    options: { ... }      // 모양과 동작
});
```

화면에는 `<canvas>` 요소를 두고, 그 위에 Chart.js가 그립니다.

### 월별 막대 차트

`resources/js/app/summary/summary.js` (일부)

```js
renderMonthlyChart = function(labels, monthly){
    draw('monthly', m$.monthlyChart, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {label: '수입', data: monthly.map(function(r){ return r.income; }), backgroundColor: INCOME_COLOR, borderRadius: 5},
                {label: '지출', data: monthly.map(function(r){ return r.expense; }), backgroundColor: EXPENSE_COLOR, borderRadius: 5}
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {display: false},
                tooltip: {callbacks: {label: function(ctx){ return ctx.dataset.label + ' ' + App.money(ctx.parsed.y); }}}
            },
            scales: {y: {beginAtZero: true, ticks: {callback: function(v){ return v >= 10000 ? (v / 10000).toLocaleString('ko-KR') + '만' : v; }}}}
        }
    });
},
```

| 부분 | 뜻 |
|---|---|
| `labels` | 가로축 이름 (`2026-09`, `2026-10` ...) |
| `datasets` | 막대 묶음. 수입, 지출 두 개를 나란히 |
| `responsive`, `maintainAspectRatio: false` | 화면 크기에 맞춰 늘고 줄며, 높이는 CSS가 정함. 휴대폰에서도 알맞게 |
| `tooltip.callbacks.label` | 막대에 마우스를 올렸을 때 `수입 1,930,000원`처럼 원 단위로 |
| `ticks.callback` | 세로축 숫자를 `193만`처럼 짧게. 긴 숫자가 축을 차지하지 않게 |

서버가 보낸 데이터(`monthly`)를 `map`으로 숫자 배열로 바꿔 넣습니다.

### 카테고리 도넛 차트

```js
draw('category', m$.categoryChart, {
    type: 'doughnut',
    data: {
        labels: rows.map(function(r){ return r.label; }),
        datasets: [{
            data: rows.map(function(r){ return r.amount; }),
            backgroundColor: rows.map(function(r, i){ return DONUT_COLORS[i % DONUT_COLORS.length]; }),
            borderWidth: 0
        }]
    },
    options: { cutout: '62%', ... }
});
```

- `cutout: '62%'`: 가운데 구멍 크기
- 색은 정해 둔 목록을 돌려 씁니다(`i % 길이`). 카테고리가 색 개수보다 많아도 오류가 나지 않습니다.
- Chart.js 기본 범례 대신 `App.h`로 직접 범례를 그려 금액과 비율(`Math.round(amount * 100 / total)`)을 함께 보여 줍니다.

## 다시 그릴 때 이전 차트 없애기

```js
/* 같은 캔버스에 다시 그리기 전에 이전 차트를 없앤다 */
draw = function(key, canvas, config){
    if(settings.charts[key]) settings.charts[key].destroy();
    settings.charts[key] = new Chart(canvas, config);
},
```

기간이나 "달력 월 / 월급 주기"를 바꾸면 차트를 다시 그립니다. 같은 `<canvas>`에 이전 차트를 지우지 않고 새로 만들면 Chart.js가 "이미 사용 중인 캔버스"라며 오류를 내거나, 마우스를 올릴 때 이전 차트가 번쩍이는 문제가 생깁니다. 만든 차트를 `settings.charts`에 기억해 두고, 다시 그리기 전에 `destroy()`로 정리합니다.

## 차트 옆에 표를 함께 두는 이유

요약 화면은 같은 데이터를 **표로도** 보여 줍니다.

```jsp
<table>
    <caption class="sr-only">월별 수입 지출 잔액</caption>
    ...
</table>
```

- 차트는 경향을 보기 좋고, 표는 정확한 금액을 읽기 좋습니다.
- `<canvas>` 안의 그림은 **화면 낭독기가 읽을 수 없습니다.** 표가 있으면 차트를 보지 못하는 사용자도 같은 정보를 얻습니다.

## 정리

- Chart.js는 `<script>` 하나로 넣고 `new Chart(canvas, {type, data, options})`로 그립니다.
- `tooltip`, `ticks`의 `callback`으로 금액을 원 단위와 만 단위로 보여 줍니다.
- 다시 그리기 전에 `destroy()`로 이전 차트를 정리합니다.
- 같은 데이터를 표로도 제공해 정확한 값과 접근성을 챙깁니다.
