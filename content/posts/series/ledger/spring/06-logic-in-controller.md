---
title: "로직은 Controller에, ServiceImpl은 DAO 연결만"
slug: "ledger-spring-logic-in-controller"
category: "development"
publishedAt: "2026-10-08"
summary: "Ledger가 검증, 분기, 세션, 트랜잭션을 Controller에 두고 ServiceImpl을 얇게 둔 규칙과, 스케줄러에서 함께 쓰는 로직의 위치를 정리합니다."
series: "ledger"
chapter: "spring"
seriesOrder: 6
---
## 이 글에서 할 일

흔히 Spring 프로젝트에서는 "Controller는 얇게, 로직은 Service에"라고 배웁니다. Ledger는 반대로 정했습니다. 이 글에서는 그 규칙이 무엇이고, 어떤 결과를 가져왔는지 정리합니다.

## 규칙

README에 적은 규칙입니다.

> 로직(검증·분기·계산·세션·`@Transactional`)은 `XxxApiController`, `ServiceImpl`은 DAO 호출만. 컨트롤러에서 try/catch 금지(`CmmnExceptionHandler`가 처리).

그래서 `ServiceImpl`은 대부분 이렇게 생겼습니다.

`ledger/settings/service/impl/SettingsServiceImpl.java` (일부)

```java
@Override
public Map<String, Object> selectUserSetting(long userId) {
    return settingsDAO.selectUserSetting(userId);
}
```

로직이 들어 있는 곳은 Controller입니다. 카테고리 저장(`saveCategory`)을 보면 입력 검사, 존재 확인, "쓰는 곳이 있으면 구분을 못 바꾼다"는 업무 규칙, 중복 이름 검사, 순서 번호 계산이 모두 Controller 메서드 안에 있습니다.

```java
// 이미 기록된 거래와 구분이 어긋나지 않게, 쓰는 곳이 있으면 구분을 바꾸지 않는다
if (!type.equals(saved.get("type")) && settingsService.countCategoryUsage(key) > 0) {
    return Response.of(Constants.IN_USE, "거래나 고정 항목에서 쓰고 있어 구분을 바꿀 수 없어요.", null);
}
```

## 이 구조에서 얻은 것

- **한 요청의 흐름이 한 메서드에 보입니다.** 입력부터 응답까지 파일을 오가지 않고 읽을 수 있습니다.
- **트랜잭션 범위가 요청 하나와 같습니다.** Controller 메서드에 `@Transactional`을 붙이면 그 요청의 모든 DB 작업이 한 묶음이 됩니다. 예를 들어 가입은 사용자, 기본 카테고리, 기본 결제수단을 한 번에 넣고, 하나라도 실패하면 모두 취소됩니다.

```java
// 가입: 사용자 + 기본 카테고리 + 기본 결제수단을 한 트랜잭션으로
@Transactional
@ResponseBody
@PostMapping("/auth/signup")
public Response signup(...) {
    ...
    authService.insertUser(user);
    authService.insertDefaultCategories(user);
    authService.insertDefaultPaymentMethods(user);
    return Response.of(Constants.SUCCESS);
}
```

- **try/catch를 쓰지 않습니다.** 예외가 나면 메서드 밖으로 나가 트랜잭션이 롤백되고, [공통 예외 처리](/posts/ledger-spring-exception-handling)가 응답을 만듭니다. 이미 롤백이 끝난 Service 트랜잭션의 예외를 사용자 안내로 바꾸는 한 곳(거래 저장)만 예외입니다.

## 예외: 여러 곳에서 쓰는 로직은 아래로

모든 로직이 Controller에 있는 것은 아닙니다. **여러 진입점에서 똑같이 지켜야 하는 규칙**은 Controller 밖에 둡니다.

| 위치 | 로직 | 이유 |
|---|---|---|
| `EntryServiceImpl` | 거래를 넣고, 고치고, 지울 때 연결된 적금 자산 잔액도 함께 조정 | 거래 화면(Controller)과 고정 항목 자동 기록(스케줄러) **둘 다** 거래를 만듦 |
| `RecurringGenerator` | 고정 항목 결제일에 거래 자동 기록 | 스케줄러, 앱 시작, 고정 항목 저장 직후에서 모두 호출 |
| `PayCycle`, `Installment`, `TransferAdjustment` 등 | 날짜와 금액 계산 | DB와 무관한 순수 계산. 여러 Controller가 씀 |

`EntryServiceImpl`의 `insertEntry`는 그래서 `@Transactional`을 직접 가집니다. 스케줄러는 Controller를 거치지 않으므로, 거래와 자산 조정을 한 묶음으로 만들려면 Service에 트랜잭션이 있어야 하기 때문입니다.

```java
@Override
@Transactional
public int insertEntry(Map<String, Object> param) {
    int inserted = entryDAO.insertEntry(param);
    adjust(param, null, 0);   // 연결된 자산 잔액 조정
    return inserted;
}
```

## 코드에서 역할을 찾는 방법

현재 Ledger의 입력 검사와 요청별 분기는 Controller에서 찾습니다. 거래와 자산 잔액의 동시 조정은 `EntryServiceImpl`에서, 자동 기록은 `RecurringGenerator`와 `TransferGenerator`에서 찾습니다. 날짜와 금액 계산은 순수 계산 클래스에 모여 있습니다.

Controller 이름이 붙은 테스트도 무엇을 호출하는지 보고 읽습니다. `SettingsApiControllerTest`는 순서 변경 입력의 ID 해석과 집합 일치를 확인하고, `RecurringApiControllerTest`는 지난 결제일 처리 여부와 다음 결제일 계산을 확인합니다. 실제 HTTP 요청 전체를 실행한 결과와는 구분해서 읽습니다.

## 정리

- Ledger는 검증, 분기, 세션, 트랜잭션을 Controller에 두고 `ServiceImpl`은 DAO 호출만 합니다.
- 요청 하나의 흐름과 트랜잭션 범위가 메서드 하나에 보이는 장점이 있습니다.
- 여러 진입점이 함께 쓰는 규칙(거래와 자산 조정, 자동 기록, 날짜 계산)은 Controller 밖에 둡니다.
- 파일 이름보다 실제 호출 관계를 기준으로 요청 처리, 공유 로직, 계산의 위치를 찾습니다.
