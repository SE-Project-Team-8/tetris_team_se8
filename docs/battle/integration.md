# 다른 담당과의 연결 계약

근거: 팀 문서 「3차 요구사항 · 역할 배정」(2026-11-16)의 공통 합의 사항("로컬·P2P는 분리 개발 → 완성 후 통합, 시작 전에 공격 줄 데이터 구조만 먼저 합의"), LB-N04.

> 전부 **제안**이다. 상대 담당과 합의하면 상태를 "합의"로 바꾸고 날짜를 적는다.

| # | 항목 | 상대 | 상태 |
| --- | --- | --- | --- |
| 1 | 공격 줄 데이터 구조 | 손동민 (P2P) | 제안 |
| 2 | 플레이어 표시 정보 | 손동민 (P2P) | 제안 |
| 3 | 화면 작업 경계와 겹치는 파일 | 조상윤·안성현 (UI), 손동민 | 제안 — **UI 담당 동의 필요** (`ui-contract.md`) |
| 4 | `block.Board` 변경 | `block` 담당 | 제안 |

## 1. 공격 줄 데이터 구조

로컬과 P2P가 같은 값을 쓴다. 로컬은 메모리로, P2P는 소켓으로 넘긴다.

```java
package com.team.tetris.common.battle;

/** 공격 줄 한 줄. 비트 c(0~9)가 1이면 c열이 차 있다. 불변이며 그대로 직렬화할 수 있다. */
public record GarbageLine(int cells) implements java.io.Serializable {
    public boolean filled(int col) { … }
}
```

- **한 번의 공격** = `List<GarbageLine>`, **위에서 아래** 순서(삭제 전 보드에서의 순서). 보통 2~4줄이고, 넘길 칸이 없는 줄이 빠지면 더 적다.
- 칸이 하나도 없는 줄과 꽉 찬 줄은 `GarbageLine`으로 만들 수 없다(생성자가 거부한다).
- 색은 담지 않는다. 받은 쪽에서 항상 회색으로 그린다.
- 열 번호는 화면 왼쪽이 0이다. 좌우를 뒤집지 않는다.
- 비트 하나짜리 정수로 둔 이유: 배열을 가진 record는 내용이 바뀔 수 있고 `equals`가 참조 비교가 된다. 정수는 불변이고 한 줄에 4바이트라 전송도 작다.
- **넘어갈 줄을 만드는 쪽은 보낸 사람**이다(`BlockBoardDriver`가 계산). 받는 쪽은 모양을 다시 계산하지 않는다.
- **대기열 규칙을 적용하는 쪽은 받는 사람**이다(`GarbageQueue.offer`). P2P에서도 받은 쪽 PC가 10줄 상한·잘라내기를 적용한다.

## 2. 플레이어 표시 정보

화면은 엔진을 직접 보지 않고 **표시 정보만** 받아 그린다(원본 p.11의 방식). 호출과 알림의 목록은 `ui-contract.md` 2~4절에 있다.

- P2P는 원격 플레이어의 표시 정보를 네트워크로 받아 같은 알림으로 넣으면 된다. 화면 코드는 로컬과 같다.
- `BoardSnapshot`은 `int[][]` 하나를 가진 record라 직렬화 대상으로 쓰기 쉽다. 전송량을 줄이는 방법(변경분만 보내기 등)은 P2P 쪽에서 정한다.
- 대전 알림의 정확한 인터페이스는 로직 PR에서 확정해 `ui-contract.md`에 옮겨 적는다.

## 3. 화면 작업 경계와 겹치는 파일

로컬 대전 담당은 화면을 만들지 않고 연결 지점까지 만든다(`decisions.md` D-12). 담당 경계는 `ui-contract.md` 1절의 표다. **UI 담당의 동의를 받아야 한다.**

같은 파일을 양쪽이 고치게 되는 곳과 처리 방법.

| 파일 | 누가 무엇을 | 제안 |
| --- | --- | --- |
| `Main.java` | 로컬: 엔진 2개·`VersusMatch`·`VersusInput` 조립, 확인용 화면 실행 인자 / UI·P2P: 화면 등록 | 대전 조립을 메서드 하나로 분리해 충돌 범위를 줄인다. 로직 PR이 먼저 들어간다 |
| `settings/*` | 로컬: `VersusKeyBindings`, 저장 형식 버전 3 | UI는 `SettingsScreen`에서 이 값을 읽고 쓴다. 로컬은 `SettingsScreen`의 화면 배치를 건드리지 않는다 |
| `ui/SettingsScreen.java` | 로컬: `GameSettings` 생성자가 바뀌어 컴파일을 맞추는 최소 수정 / UI: 대전 키 영역 | 로컬이 먼저 컴파일만 맞추고, UI가 그 위에 영역을 추가 |
| `ui/MainMenuScreen.java` | UI: "대전 모드" / P2P: "P2P 대전" | 항목 순서를 게임 시작 → 아이템 모드 → 대전 모드 → P2P 대전 → 설정 → 스코어보드 → 게임 종료로 제안 |
| `ui/ScreenRouter.java` | UI·P2P가 화면 이름 상수 추가 | P2P는 `P2P_` 접두어 |
| `docs/design/design-spec.md` §6 | 대기열 표현이 "가로 바"로 적혀 있음 | 격자 표현으로 고침 (3차 p.4, `screens.md` 3절) — UI 담당 |

## 4. `block.Board` 변경

`block` 패키지에 다음을 추가한다(`design.md` 3.2). `block`은 game·ui를 import하지 않는다는 규칙은 유지한다.

- 칸별 회색 여부 저장과 `isGarbage(row, col)`.
- `insertRowsAtBottom(List<GarbageLine>)`: 아래에 줄 삽입, 위로 넘치면 true.
- 기존 줄 삭제·칸 삭제·초기화가 회색 표시를 함께 처리.

기존 공개 메서드의 동작과 반환값은 바꾸지 않는다. `BoardTest` 등 기존 테스트는 수정 없이 통과해야 한다.

## 5. 통합 일정 (11/26~27)

1. 로컬 로직 PR(공격 줄·대기열·엔진 연결·`VersusMatch`)이 먼저 `main`에 들어간다.
2. P2P가 `GarbageLine`·`GarbageQueue`·`AttackLink`를 가져다 쓰고, 화면은 UI 담당의 대전 화면을 함께 쓴다.
3. 겹치는 규칙(승패 판정, 시간제한)은 `VersusMatch`를 함께 쓰는지, 원격용 변형이 필요한지 그때 정한다.
