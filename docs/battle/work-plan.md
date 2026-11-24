# 작업 계획: 파일 배치와 커밋 단위

로컬 대전 담당(윤성빈)의 구현 계획이다. 무엇을 어디에 두고, 어떤 순서와 단위로 커밋하는지 적는다.
범위는 `README.md`, 설계는 `design.md`, 연결 지점은 `ui-contract.md`를 본다.

> 2026-11-24 작성. 커밋 1~4는 코드 초안을 써 보며 확인한 내용이고, 5 이후는 계획이다.
> 구현하다 달라지면 이 문서와 `design.md`를 같이 고친다.
> 작업은 이 문서를 기준으로 진행하고, 진행 상황은 바로 아래 "진행 현황"에 적는다.

## 진행 현황

**이 표가 작업의 기준이다.** 어느 기기에서든 여기를 보고 이어서 한다. 커밋할 때 해당 줄의 상태를 같은 커밋에서 고치고, 커밋한 뒤 바로 푸시한다.

마지막 갱신: 2026-11-24 — 커밋 10 완료. 수동 확인은 남음.

| # | 커밋 | 상태 | 메모 |
| --- | --- | --- | --- |
| 1 | 공격 줄 값과 공격 대기열 | 완료 | `Player` 테스트는 `GarbageLineTest`에 함께 둠 |
| 2 | 보드에 회색 줄 저장과 아래 삽입 | 완료 | |
| 3 | 줄 삭제 결과에 공격 줄 모양 | 완료 | |
| 4 | 엔진에 공격을 보내고 받는 지점 | 완료 | 초안의 `GameEngine.newSwingTimer()`는 쓰는 곳이 생기는 커밋 6으로 미룸 |
| 5 | 대전 한 판을 진행하는 `VersusMatch` | 완료 | 일반 방식만. 방식별 처리는 커밋 6 |
| 6 | 시간제한 대전과 아이템 대전 | 완료 | 제한 시간 타이머는 `VersusMatch` 생성자로 주입(기본값 `GameEngine.newSwingTimer()`). 일시정지로 끊긴 1초는 이어서 잰다. 남은 시간 조회 `remainingSeconds()` 추가 |
| 7 | 연결 지점 문서를 구현에 맞춤 | 완료 | `ui-contract.md` 2·4절을 코드와 같게. 3·5절(`VersusInput`, 확인용 화면)은 PR 2 계획으로 표시 |
| — | **PR 1 올리기** | 완료 | #70, 머지됨. PR 2는 머지된 `main`에서 분기 |
| 8 | 대전 키 값 | 완료 | 일시정지·나가기 키와의 겹침은 대전 키를 바꿀 때만 거부. 1인용 쪽에서 생긴 겹침은 `versusKeyClashes()`로 알림(input.md 4절) |
| 9 | 2인 키 입력 분배와 누름 반복 | 완료 | `game`이 `settings`를 import하지 않도록 키 표(`Map<Integer, PlayerMove>`)를 받음. 표는 `GameSettings.versusKeyMap()`. 커밋 8의 `VersusKeyBindings.Assignment`는 `PlayerMove`로 바꿈 |
| 10 | 대전 조립과 확인용 화면 | 완료 | `--versus=normal\|item\|time`. 화면 테스트(키·창 크기·그리기)와 실행 인자 테스트 추가. **두 사람 수동 확인(`test-plan.md` 3절)은 아직** |
| 11 | 대전 키를 설정 파일에 저장 | 대기 | |
| 12 | 수동 확인 결과와 문서 마무리 | 대기 | |
| — | **PR 2 올리기** | 대기 | |

상태: **대기** (시작 전) → **초안** (본 코드는 있으나 테스트가 없어 커밋 전) → **완료** (테스트와 함께 커밋하고 푸시함).

**다음에 할 일.**
1. 수동 확인: `./gradlew run --args="--versus=normal"`로 두 사람이 한 판을 끝까지 한다. `test-plan.md` 3절의 "확인용" 항목을 보고, 누름 반복(170ms/50ms)이 1인 모드와 비슷한지 본다. 결과는 커밋 12에서 적는다.
2. 커밋 11: `SettingsRepository` 형식 버전 3, `versus.p1.MOVE_LEFT` 같은 키로 대전 키 저장, 버전 1·2 파일은 대전 키 기본값. `SettingsRepositoryVersusKeysTest`.

### 다른 기기에서 이어서 하기

```bash
git fetch
git switch feat/versus-play          # 처음이면 원격 브랜치를 따라 만들어진다
git pull
```

PR 1(`feat/versus-core`)은 머지됐다. PR 2는 `feat/versus-play`만 받으면 된다.

## 1. 브랜치와 PR

```text
main
 └─ docs/local-battle        문서 (이 문서 포함)
     └─ feat/versus-core     PR 1: 대전 규칙 — 커밋 1~7
         └─ feat/versus-play PR 2: 입력·조립·확인용 화면·키 저장 — 커밋 8~12
```

- 코드 브랜치를 문서 브랜치 위에 쌓는다. 코드를 쓰면서 문서를 같이 보고 고치기 위해서다. `main`에 머지되면 커밋만 남으므로 어느 브랜치에서 올렸는지는 상관없다.
- **PR 1이 먼저다.** UI 담당과 P2P 담당이 PR 1의 연결 지점을 받아야 작업을 시작할 수 있다.
- 실제 진행: PR 1은 문서 커밋까지 함께 `feat/versus-core` → `main`으로 올려 머지했다(#70). PR 2(`feat/versus-play`)는 머지된 `main`에서 분기했다. 대전 작업은 PR 단위로 바로 `main`에 머지한다.
- PR 설명은 `.github/PULL_REQUEST_TEMPLATE.md`의 네 항목(무슨 기능 / 어떻게 구현 / 테스트 여부 / 관련 이슈)을 채운다.

## 2. 커밋 규칙

- 메시지는 기존 기록을 따른다: `종류(범위): 한국어 요약`. 종류는 `feat`·`fix`·`refactor`·`test`·`docs`, 범위는 `battle`·`block`·`game`·`settings`·`ui`.
- **커밋 하나는 코드와 그 코드의 테스트를 함께 담는다.** 커밋마다 `./gradlew build`가 통과해야 한다(CI가 푸시마다 빌드와 커버리지 50% 기준을 검사한다).
- **기존 테스트 파일은 고치지 않는다.** 고쳐야 통과한다면 1인 모드 동작이 달라진 것이므로 코드를 다시 본다. (LB-I10)
- 커밋 하나는 한 패키지 층의 변경 하나를 담는다. 아래 표의 단위를 합치거나 쪼개야 하면 표를 먼저 고친다.

## 3. 파일 배치

`(새)`는 새 파일, `(변경)`은 기존 파일이다. 경로는 `src/main/java/com/team/tetris/` 기준이고, 테스트는 `src/test/java/com/team/tetris/`의 같은 패키지에 둔다.

```text
common/
├── battle/                          (새 패키지) block·game·ui·P2P가 함께 쓰는 값
│   ├── package-info.java            (새)
│   ├── GarbageLine.java             (새) 공격 줄 한 줄. 열별 채움 여부 10비트
│   ├── Player.java                  (새) P1, P2
│   └── VersusMode.java              (새) NORMAL, ITEM, TIME_LIMIT
├── constants/GameConstants.java     (변경) 공격 최소 줄 수 2, 대기열 10줄, 제한 시간 180초
└── events/CellCodes.java            (변경) 회색 칸 코드 GARBAGE, isGarbage

block/
└── Board.java                       (변경) 회색 칸 저장, isGarbage, insertRowsAtBottom

game/
├── BlockBoardDriver.java            (변경) 공격 줄 모양 계산, insertGarbage, 스냅샷의 회색 코드
├── GameEngine.java                  (변경) AttackLink, LockResult.attackLines, BoardDriver.insertGarbage
└── versus/                          (새 패키지) 화면 없는 대전 규칙
    ├── package-info.java            (새)
    ├── GarbageQueue.java            (새) 공격 대기열, 최대 10줄
    ├── VersusMatch.java             (새) 엔진 2개·대기열 2개·승패·일시정지·제한 시간
    ├── VersusListener.java          (새) 대전 알림 (대기열·레벨·남은 시간·일시정지·결과)
    ├── MatchResult.java             (새) 승자·종료 사유·두 점수
    ├── PlayerMove.java              (새) 플레이어 한 명의 조작 하나. 키 표의 값
    └── VersusInput.java             (새) 키 분배와 누름 반복. 키 코드와 키 표만 받는다

settings/
├── VersusKeyBindings.java           (새) P1·P2 대전 키
├── GameSettings.java                (변경) 대전 키 항목
└── SettingsRepository.java          (변경) 형식 버전 3, 이전 버전 읽기

ui/
├── VersusCheckScreen.java           (새) 확인용 화면. 꾸미지 않음
└── SettingsScreen.java              (변경) GameSettings가 바뀐 데 따른 컴파일 맞춤만

Main.java                            (변경) 대전 조립, 확인용 화면 실행 인자
```

테스트 파일.

```text
common/battle/GarbageLineTest.java       Player.opponent 포함
block/BoardGarbageTest.java
game/AttackLinesTest.java            드라이버의 공격 줄 모양
game/VersusEngineTest.java           엔진과 AttackLink
game/versus/GarbageQueueTest.java
game/versus/VersusMatchTest.java
game/versus/VersusItemTest.java
game/versus/VersusInputTest.java
settings/VersusKeyBindingsTest.java
settings/SettingsRepositoryVersusKeysTest.java   기존 SettingsRepositoryTest는 그대로 둔다
```

### 설계 문서에서 달라진 배치

코드 초안을 쓰면서 `design.md`의 계획과 달라진 곳이다. `design.md`도 이에 맞춰 고쳤다.

| 항목 | 처음 계획 | 바뀐 것 | 이유 |
| --- | --- | --- | --- |
| 회색 칸 코드 | `CellCodes.GARBAGE = 7`, `isValid`가 받아들임 | `GARBAGE = -2`, `isGarbage(code)`로 구분 | 기존 `CellCodesTest`가 7을 잘못된 코드로 검사한다. 기존 테스트를 고치지 않는다는 규칙을 지킨다 |
| `AttackLink`의 위치 | `game/versus/AttackLink` | `GameEngine.AttackLink` (안쪽 인터페이스) | `GameEngine`이 `game.versus`를, `game.versus`가 `GameEngine`을 import하는 순환을 피한다. `BoardDriver`, `DropTimer`도 같은 방식이다 |
| `Player` | 위치를 정하지 않음 | `common/battle/Player` | 알림과 결과가 함께 쓰고 P2P도 쓴다 |
| `GarbageLine`의 제약 | 10칸 채움 여부 | 빈 줄과 꽉 찬 줄은 만들 수 없음 | 빈 줄은 넘길 것이 없다(D-06). 꽉 찬 줄은 들어가자마자 지워질 줄이라 생길 수 없어야 한다 |
| 제한 시간용 타이머 | 엔진의 `DropTimer`를 주입 | `GameEngine.newSwingTimer()`를 추가해 얻음 | 실시간 타이머 구현(`SwingDropTimer`)이 패키지 밖에서 보이지 않는다 |

## 4. 커밋 단위

### PR 1 — `feat/versus-core`: 대전 규칙

| # | 커밋 메시지 | 담는 파일 | 테스트 | 요구사항·결정 |
| --- | --- | --- | --- | --- |
| 1 | `feat(battle): 공격 줄 값과 공격 대기열 추가` | `common/battle/*`, `GameConstants`, `game/versus/GarbageQueue`, `game/versus/package-info` | `GarbageLineTest`, `GarbageQueueTest` | LB-F09~F12, D-02 |
| 2 | `feat(block): 보드에 회색 줄 저장과 아래 삽입 추가` | `Board` | `BoardGarbageTest` | LB-F07, F08 |
| 3 | `feat(game): 줄 삭제 결과에 공격 줄 모양 추가` | `CellCodes`, `BlockBoardDriver`, `GameEngine`의 `LockResult`와 `BoardDriver.insertGarbage` | `AttackLinesTest` | LB-F04, F05, D-05, D-06 |
| 4 | `feat(game): 엔진에 공격을 보내고 받는 지점(AttackLink) 추가` | `GameEngine`의 `AttackLink`, `lockAndAdvance`, 생성자 | `VersusEngineTest` | LB-F04, F07, D-01, D-03 |
| 5 | `feat(battle): 대전 한 판을 진행하는 VersusMatch 추가` | `VersusMatch`, `VersusListener`, `MatchResult` | `VersusMatchTest` (시작, 공격 전달, 승패, 일시정지, 중단, 알림) | LB-F03, F13, LB-N02 |
| 6 | `feat(battle): 시간제한 대전과 아이템 대전` | `VersusMatch`의 제한 시간과 아이템 방식 전환 | `VersusMatchTest` 추가분, `VersusItemTest` | LB-F16, F17, D-10 |
| 7 | `docs(battle): 연결 지점 문서를 구현에 맞춤` | `ui-contract.md`, `design.md`, `integration.md` | — | — |

- 1~4는 서로 의존하는 순서다. 1(값) → 2(보드가 값을 받음) → 3(드라이버가 값을 만들고 보드에 넘김) → 4(엔진이 주고받음).
- 3과 4를 나눈 이유: 3은 "어떤 줄이 넘어가는가", 4는 "언제 넘어가고 언제 들어오는가"다. 리뷰할 규칙이 다르다.
- 4까지 끝나면 1인 모드는 `AttackLink.NONE`으로 이전과 똑같이 동작해야 한다. 이 시점에 기존 테스트 전체가 통과하는지 확인한다.
- 5와 6을 나눈 이유: 5만으로 일반 대전 한 판이 끝까지 돈다. 6은 그 위에 얹는 두 방식이다.
- 7에서 `ui-contract.md`의 메서드 이름과 모양을 실제 코드와 같게 만든다. UI 담당이 이 문서만 보고 붙일 수 있어야 한다.

### PR 2 — `feat/versus-play`: 입력·조립·확인용 화면·키 저장

| # | 커밋 메시지 | 담는 파일 | 테스트 | 요구사항·결정 |
| --- | --- | --- | --- | --- |
| 8 | `feat(settings): 대전 키 값(VersusKeyBindings) 추가` | `VersusKeyBindings`, `GameSettings`, `SettingsScreen`(컴파일 맞춤) | `VersusKeyBindingsTest` | LB-I11, D-11, D-13 |
| 9 | `feat(battle): 2인 키 입력 분배와 누름 반복(VersusInput) 추가` | `VersusInput`, `PlayerMove`, `GameSettings.versusKeyMap` | `VersusInputTest`, `VersusKeyBindingsTest` 추가분 | LB-F18, LB-I01 |
| 10 | `feat(ui): 대전 조립과 확인용 화면 추가` | `Main`, `VersusCheckScreen` | 수동 확인 (`test-plan.md` 3절) | LB-F03, LB-I01, D-12 |
| 11 | `feat(settings): 대전 키를 설정 파일에 저장` | `SettingsRepository` | `SettingsRepositoryVersusKeysTest` | LB-I11 |
| 12 | `docs(battle): 수동 확인 결과와 문서 마무리` | `test-plan.md`, `ui-contract.md`, `input.md` | — | — |

- 8이 9보다 먼저인 이유: 입력 처리가 처음부터 설정 값으로 키를 받게 만든다. 8에서는 값과 기본값만 넣고 저장은 11로 미룬다. 그 사이에는 기본 키로 동작한다.
- 10에서 처음으로 두 사람이 실제로 플레이할 수 있다. 여기서 동시 키 입력을 손으로 확인하고, 반복 지연·간격 수치를 맞춘다.
- 11은 10 뒤다. 대전이 한 판 끝까지 도는 것을 확인하는 일이 저장보다 급하다.

## 5. 11/25 진행 순서와 자를 곳

| 순서 | 할 일 | 끝났다는 기준 |
| --- | --- | --- |
| ① | 커밋 1~4 | 기존 테스트 전체와 새 테스트 통과. 1인 모드를 실행해 이전과 같은지 확인 |
| ② | 커밋 5~7, PR 1 올리기 | 테스트에서 일반·아이템·시간제한 대전이 승패까지 진행. UI·P2P 담당에게 PR 알림 |
| ③ | 커밋 8~10 | 확인용 화면에서 두 사람이 한 판을 끝까지 플레이 |
| ④ | 커밋 11~12, PR 2 올리기 | 대전 키를 바꿔 저장하고 다시 실행해도 유지 |

- 커밋 1~4는 코드 초안이 있고 컴파일까지 확인했다. 테스트를 쓰면서 커밋 단위로 나눠 올린다.
- **시간이 모자라면 ④를 11/25 뒤로 넘긴다.** 대전 키는 기본값으로 동작하므로 저장이 없어도 대전은 된다. ①~③이 11/25의 최소 목표다.
- ②가 늦어지면 UI 담당의 화면 작업도 늦어진다. ②를 가장 먼저 지킨다.

## 6. 커밋 전 확인

- [ ] `./gradlew build` 통과 (테스트와 커버리지 기준 포함)
- [ ] 기존 테스트 파일을 고치지 않았다
- [ ] 새 공개 클래스·메서드에 역할을 설명하는 주석이 있다 (파일 머리 주석 포함, 기존 `game/item`의 방식)
- [ ] `block`이 game·ui·settings·scoreboard를 import하지 않는다. `game`이 ui를 import하지 않는다
- [ ] 문서의 클래스·메서드 이름이 코드와 같다
- [ ] "진행 현황" 표의 해당 줄과 "마지막 갱신", "다음에 할 일"을 고쳤다
- [ ] 커밋한 뒤 푸시했다 (다른 기기에서 이어서 할 수 있게)
