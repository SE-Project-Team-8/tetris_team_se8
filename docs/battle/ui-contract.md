# UI 연결 지점

로컬 대전 담당(윤성빈)은 화면을 만들지 않는다. 대전 로직과 **UI가 그 위에 화면을 얹을 수 있는 연결 지점**까지 만든다(`decisions.md` D-12).
이 문서는 UI 담당(조상윤·안성현)에게 넘기는 계약이다. P2P 담당(손동민)도 같은 연결 지점을 쓴다.

> 구현 전 초안이다. 이름과 메서드 모양은 로직 PR에서 확정하고, 그때 이 문서를 실제 코드에 맞춰 고친다.
> **이 범위 나눔은 UI 담당의 동의가 필요하다.** 역할 배정 문서와 다르기 때문이다(1절).

## 1. 누가 무엇을 만드는가

| 구분 | 로컬 대전 (윤성빈) | UI (조상윤·안성현) |
| --- | --- | --- |
| 대전 규칙 | 공격 줄, 대기열, 보드 삽입, 승패, 시간제한 | — |
| 조립 | `Main`에서 엔진 2개와 `VersusMatch`를 만들어 연결 | 화면을 `ScreenRouter`에 등록 |
| 키 입력 | 키 분배와 누름 반복 (`VersusInput`) | 창의 키 이벤트를 `VersusInput`에 넘겨줌 |
| 대전 키 설정 | 값(`VersusKeyBindings`)과 설정 파일 저장 | 설정 화면의 대전 키 영역 |
| 화면 | **확인용 화면** 하나 (꾸미지 않음, 5절) | 시작 메뉴 항목, 방식 선택·대전·승자 화면, 창 크기 변경, 일시정지 화면 |
| 테스트 | 로직과 입력의 단위 테스트 | 화면 테스트 |

역할 배정 문서(2026-11-16)는 "메뉴·모드 선택", "대기열 표시 영역", "승자 화면 표시"를 로컬 대전 담당 항목에 적었다. 위 표는 그 화면들의 **그리기**를 UI 담당으로 옮기고, 로컬 대전 담당은 그 화면에 필요한 **데이터와 동작**을 제공하는 것으로 바꾼 것이다.

## 2. UI가 호출하는 것

`VersusMatch` 하나만 상대한다. 모두 Swing EDT에서 호출한다(`GameEngine`과 같은 계약).

| 호출 | 언제 | 비고 |
| --- | --- | --- |
| `start(VersusMode mode)` | 방식 선택 화면에서 Enter, 승자 화면에서 "다시 대전" | `NORMAL` / `ITEM` / `TIME_LIMIT`. 이전 판의 보드·점수·대기열을 비우고 새 판을 시작 |
| `pause()` / `resume()` | 일시정지 키, 화면이 가려질 때 / 돌아올 때 | 두 보드와 제한 시간이 함께 멈춘다 |
| `stop()` | 나가기를 확인했을 때 | 승패 없이 끝난다. 결과 알림이 오지 않는다 |
| `state()` | 화면이 다시 보일 때 | 준비 / 진행 중 / 일시정지 / 종료 |
| `mode()`, `remainingSeconds()`, `level(player)` | 처음 그릴 때 | 알림을 기다리지 않고 현재 값을 읽는다 |

블럭 조작은 UI가 직접 호출하지 않는다. 키 이벤트를 3절의 `VersusInput`에 넘기면 된다.

## 3. UI가 키 이벤트를 넘기는 곳

`VersusInput`은 화면을 모르는 클래스다. 키 코드의 눌림과 뗌만 받는다. 어느 키가 누구의 어떤 동작인지, 누르고 있을 때의 반복, 하드드롭 한 번만 실행 같은 처리는 전부 안에서 한다(`input.md`).

| 호출 | 언제 |
| --- | --- |
| `keyPressed(int keyCode)` | 대전 화면이 보이는 동안 창에서 `KEY_PRESSED`를 받을 때마다 (OS 자동 반복 포함, 걸러 내지 않고 그대로) |
| `keyReleased(int keyCode)` | `KEY_RELEASED`를 받을 때마다 |
| `releaseAll()` | 화면을 떠날 때, 창이 포커스를 잃을 때, 대화상자를 띄울 때 |

- 포커스가 어디에 있든 받도록 `KeyboardFocusManager`의 `KeyEventDispatcher`로 등록해 넘긴다(요구사항 p.7). `HeldKeys`, `PauseScreen`이 쓰는 방식과 같다.
- `keyPressed`는 그 키가 P1·P2의 조작 키여서 처리했으면 true를 돌려준다.
- **일시정지 키와 나가기 키는 `VersusInput`이 처리하지 않는다.** 화면 전환과 확인 대화상자가 따르는 키라 UI가 직접 받아 `match.pause()`·`match.stop()`을 호출한다(1인 모드의 화면들이 하는 방식과 같다).

```java
// 대전 화면의 onShow / onHide 에서 (예시)
KeyEventDispatcher forward = event -> {
    switch (event.getID()) {
        case KeyEvent.KEY_PRESSED -> { return input.keyPressed(event.getKeyCode()); }
        case KeyEvent.KEY_RELEASED -> input.keyReleased(event.getKeyCode());
    }
    return false;
};
KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(forward);   // onShow
// onHide: removeKeyEventDispatcher(forward); input.releaseAll();
```

## 4. UI가 받는 것

### 4.1 플레이어별 표시 정보

플레이어(P1, P2)마다 기존 `GameEventListener`를 하나씩 등록한다. 1인 모드 화면이 받는 것과 같은 알림이다.

| 알림 | 내용 |
| --- | --- |
| `onBoardUpdated(BoardSnapshot)` | 20×10 보드(낙하 블럭 포함). 받은 회색 칸의 코드는 `CellCodes.GARBAGE` |
| `onLandingUpdated(falling, landing)` | 낙하 블럭과 착지 예고 칸 |
| `onLinesCleared(rows, boardBeforeClear)` | 줄 삭제 표시용 |
| `onScoreChanged(int)` | 점수 |
| `onNextBlocksChanged(int[])` | 다음 블럭 |
| `onFeverActivated`, `onSlowDownActivated` | 아이템 효과 표시용 (아이템 대전) |

`onGameOver`와 `onPauseStateChanged`는 플레이어별로 오지만 화면 전환에는 쓰지 않는다. 승패와 일시정지는 4.2의 대전 알림으로 처리한다.

### 4.2 대전 알림

`VersusListener`(계획) 하나를 등록한다.

| 알림 | 내용 | 관련 요구사항 |
| --- | --- | --- |
| `onQueueChanged(player, List<GarbageLine>)` | 그 플레이어의 공격 대기열. 위에서 아래 순서, 줄마다 10칸의 채움 여부 | LB-F06 |
| `onLevelChanged(player, level)` | 레벨 | LB-I02 |
| `onTimeChanged(remainingSeconds)` | 시간제한 대전의 남은 시간. 1초마다 | LB-I08 |
| `onPauseChanged(paused)` | 대전 전체의 일시정지 여부 | LB-I03 |
| `onMatchEnded(MatchResult)` | 승자(P1 / P2 / 무승부), 종료 사유(더 쌓을 수 없음 / 시간 종료), 두 사람의 점수. 한 판에 한 번 | LB-F13, F14 |

### 4.3 그릴 때 필요한 값

| 값 | 어디서 |
| --- | --- |
| 칸의 색·문자 | 기존 `CellCodes`, `ColorScheme`. 회색 칸은 `CellCodes.GARBAGE`이고 `UiColors.GARBAGE`·`GARBAGE_GLYPH`로 그린다 |
| 화면에 안내할 대전 키 | `VersusKeyBindings.keyFor(player, action)` |
| 일시정지·나가기 키 | 1인용 `KeyBindings`의 `PAUSE`, `QUIT` |
| 제한 시간, 대기열 상한 | `GameConstants`의 상수 |

## 5. 확인용 화면

로컬 대전 담당이 만드는 꾸미지 않은 화면 하나다.

- **목적**: ① 한 판이 처음부터 승패까지 도는지 눈으로 확인, ② 두 사람이 동시에 키를 누르는 조작감(LB-I01) 확인, ③ 이 문서의 연결 지점을 실제로 쓰는 예제.
- **내용**: 보드 2개, 플레이어별 점수·다음 블럭·레벨, 공격 대기열 격자, 남은 시간, 끝났을 때의 결과 문구. 기존 셀 색만 쓰고 연출은 없다.
- **띄우는 방법**: 시작 메뉴를 건드리지 않도록 실행 인자로 띄운다(예: `./gradlew run --args="--versus=normal"`). 정식 진입 경로는 UI 담당이 만드는 시작 메뉴 항목이다.
- UI 담당은 이 화면을 고쳐 쓰거나 새 화면으로 바꾼다. 정식 화면이 들어오면 확인용 화면을 남길지 지울지 그때 정한다.

## 6. UI 담당이 할 일

화면에 대한 요구사항은 `screens.md`에 정리했다.

- [ ] 시작 메뉴에 "대전 모드" 항목 추가 (LB-F01)
- [ ] 게임 방식 선택 화면 → `match.start(mode)` (LB-F02, LB-I06)
- [ ] 2보드 대전 화면: 4.1·4.2의 알림을 받아 그리기 (LB-F06, F08, LB-I02, I08)
- [ ] 대전 화면에서 키 이벤트를 `VersusInput`에 넘기기 (3절)
- [ ] 승자 화면: `onMatchEnded`를 받아 표시, 버튼 세 개 (LB-F14, LB-I07)
- [ ] 일시정지·나가기 (LB-I03)
- [ ] 대전 화면에 들어갈 때 창 폭 넓히기, 나올 때 되돌리기 (LB-I05)
- [ ] 화면에 대전 키 안내 (LB-I04)
- [ ] 설정 화면에 대전 P1·P2 키 영역 (LB-I11, `input.md` 4절)
- [ ] 색맹 모드에서 회색 줄이 구분되는지 확인 (LB-I05)
