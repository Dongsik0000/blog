---
title: "패키지, 접근 제어, 인터페이스"
slug: "ledger-java-maven-interface-access"
category: "development"
publishedAt: "2026-10-08"
summary: "package와 import, public과 private, static과 final, 그리고 Service 인터페이스와 ServiceImpl 구현 클래스의 관계를 Ledger 코드로 설명합니다."
series: "ledger"
chapter: "java-maven"
seriesOrder: 3
---
## 이 글에서 할 일

Ledger의 모든 기능은 `XxxService`(인터페이스)와 `XxxServiceImpl`(구현) 짝을 가집니다. 이 구조를 이해하려면 패키지, 접근 제어, 인터페이스를 알아야 합니다.

## package와 import

```java
package ledger.settings.service.impl;

import ledger.settings.dao.SettingsDAO;
import ledger.settings.service.SettingsService;
import org.springframework.stereotype.Service;
```

- `package`: 이 파일이 속한 패키지(폴더)입니다. 파일 위치 `ledger/settings/service/impl/`과 반드시 같아야 합니다.
- `import`: 다른 패키지의 클래스를 이름만으로 쓰겠다는 선언입니다. import하지 않으면 `ledger.settings.dao.SettingsDAO`처럼 전체 이름을 써야 합니다.
- 같은 패키지의 클래스와 `java.lang`(String, Long 등)은 import 없이 씁니다.

## 접근 제어

| 키워드 | 접근할 수 있는 곳 |
|---|---|
| `public` | 어디서나 |
| (없음) | 같은 패키지 안에서만 |
| `private` | 같은 클래스 안에서만 |

Ledger 코드에서는 다음 규칙이 보입니다.

- 다른 클래스가 쓰는 메서드는 `public`
- 클래스 안에서만 쓰는 도우미 메서드와 필드는 `private`
- 테스트가 직접 호출해야 하는 Controller의 도우미 메서드는 접근 제어자를 생략해 **같은 패키지에서만** 보이게 둡니다. 테스트 클래스를 같은 패키지(`ledger.settings.controller`)에 두면 호출할 수 있습니다.

`SettingsApiControllerTest.java`

```java
assertEquals(List.of(3L, 1L, 2L), SettingsApiController.ids(List.of(3, 1L, 2)));
```

`ids`는 `public`이 아니지만 테스트가 같은 패키지에 있어서 부를 수 있습니다. 외부에 공개하지 않으면서 테스트는 할 수 있는 방법입니다.

## static과 final

| 키워드 | 붙는 곳 | 뜻 |
|---|---|---|
| `static` | 필드, 메서드 | 객체가 아니라 **클래스에** 하나만 있음. `Constants.SUCCESS`처럼 바로 씀 |
| `final` | 필드, 변수 | 한 번 정하면 바꿀 수 없음 |
| `final` | 클래스 | 상속할 수 없음 |
| `static final` | 필드 | 상수 |

`ledger/cmmn/util/Constants.java`

```java
public static final String SUCCESS = "00"; // 공통 응답 성공
public static final String INVALID = "90"; // 입력 오류
```

결과 코드를 문자열 그대로 여기저기 쓰면 오타가 나도 모릅니다. 상수로 모아 두면 `Constants.INVALID`처럼 이름으로 쓰고, 값을 바꿀 때 한 곳만 고칩니다. 화면 JS의 `App.CODE`도 같은 값을 씁니다.

## 인터페이스와 구현

**인터페이스**는 "이런 기능이 있다"는 약속만 적고, 구현은 다른 클래스가 합니다.

`ledger/settings/service/SettingsService.java` (일부)

```java
public interface SettingsService {

    Map<String, Object> selectUserSetting(long userId);

    int updateUserSetting(Map<String, Object> param);
}
```

`ledger/settings/service/impl/SettingsServiceImpl.java` (일부)

```java
@Service("settingsService")
public class SettingsServiceImpl implements SettingsService {

    @Autowired
    private SettingsDAO settingsDAO;

    @Override
    public Map<String, Object> selectUserSetting(long userId) {
        return settingsDAO.selectUserSetting(userId);
    }
}
```

| 부분 | 뜻 |
|---|---|
| `implements SettingsService` | 이 클래스가 인터페이스의 약속을 지킨다 |
| `@Override` | 인터페이스(또는 부모)의 메서드를 구현한다는 표시. 이름이나 파라미터를 틀리면 컴파일 오류로 알려 줌 |

Controller는 구현 클래스가 아니라 **인터페이스 타입**으로 받습니다.

```java
@Autowired
private SettingsService settingsService;
```

Controller는 `SettingsService`라는 약속만 알고, 실제로 어떤 클래스가 일하는지는 모릅니다. Spring이 `SettingsService`를 구현한 빈(`SettingsServiceImpl`)을 찾아 넣어 줍니다([빈과 의존성 주입](/posts/ledger-spring-beans-di)).

### 이 구조를 쓰는 이유

- 구현을 바꿔도 Controller는 고칠 필요가 없습니다.
- 전자정부 프레임워크 프로젝트의 표준 구조라, 실무 코드와 같은 모양입니다.

Ledger에서는 `ServiceImpl`이 DAO 호출을 그대로 넘기는 얇은 층입니다. 로직을 어디에 두었는지는 [로직을 Controller에 둔 구조](/posts/ledger-spring-logic-in-controller)에서 다룹니다.

## 정리

- `package`는 파일 위치와 같고, `import`로 다른 패키지의 클래스를 씁니다.
- 외부에 공개할 것만 `public`, 나머지는 `private`이나 패키지 범위로 둡니다.
- 결과 코드 같은 값은 `static final` 상수로 모읍니다.
- `Service` 인터페이스와 `ServiceImpl` 구현을 나누고, 사용하는 쪽은 인터페이스 타입으로 받습니다.
