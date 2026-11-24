# P2P 대전 인수인계

3차 요구사항의 P2P 1대1 대전은 손동민이 담당한다. P2P 모듈은 TCP 접속, 대기실 상태, 경기 동기화, 공격 전달, 결과 처리와 재대전을 제공한다. 시작 메뉴, 화면 등록과 화면 구현은 UI 담당 범위다. 접속 자동화와 테스트 커버리지 관리는 테스트 담당 범위다. [이슈 #71](https://github.com/SE-Project-Team-8/tetris_team_se8/issues/71) · [3차 요구사항](requirments3.md#p2p-대전-모드-개발-p8)

## UI 담당

화면에서 [`P2pSession`](../src/main/java/com/team/tetris/p2p/P2pSession.java) 하나를 만들어 사용한다. 세션 생성과 조작은 Swing 이벤트 디스패치 스레드(EDT)에서 하고, 세션의 변경 콜백에서 현재 값을 읽어 화면을 갱신한다. 호스트는 `P1`, 클라이언트는 `P2`다. `localPlayer()`로 내 보드를 구분한다.

```java
P2pSession session = new P2pSession(items, () -> settings.difficulty(), this::refreshP2p);
```

| 화면 상태 | 호출과 표시 |
| --- | --- |
| `IDLE` | `host()` 또는 `join(ip)`를 호출한다. `recentIp()`에서 최근 접속 주소를 읽는다. `hasError()`가 참이면 `status()`를 오류로 표시한다. |
| `CONNECTING` | `status()`를 표시한다. 호스트 대기 상태의 문구에는 접속용 IP와 고정 TCP 포트 47820이 포함된다. 취소는 `close()`를 호출한다. |
| `LOBBY` | 호스트만 `selectMode(mode)`를 호출할 수 있다. 양쪽은 `mode()`, `localReady()`, `remoteReady()`를 표시하고 시작 버튼은 `ready()`에 연결한다. 모드가 바뀌면 준비 상태를 다시 받는다. |
| `PLAYING` | `handle(GameAction)`으로 조작과 일시정지를 전달한다. 보드는 `board(Player.P1)`와 `board(Player.P2)`에서 읽는다. 시간제한 모드에서는 `seconds()`, 일시정지는 `paused()`를 사용한다. |
| `FINISHING` → `LOBBY` | 결과 교환이 끝날 때까지 조작을 막는다. 대기실로 돌아오면 `result()`에서 승자·종료 사유·양쪽 점수를 읽는다. 같은 연결에서 두 플레이어가 다시 `ready()`를 호출하면 재대전한다. |

- `join(ip)`가 `true`를 반환하면 접속 시도가 시작된 것이다. 접속 성공은 `state() == LOBBY`로 확인한다. 성공한 클라이언트 IP는 세션이 저장한다. `recentIp()`는 초기 기록이 없으면 빈 문자열을 반환하고, 파일을 읽지 못하면 `IOException`을 던진다. 저장 실패는 `recentIpSaveFailed()`로 확인한다.
- `BoardState`에는 20×10 보드, 착지 예고 위치, 다음 블록 코드, 점수, 레벨, 공격 대기열이 들어 있다. 첫 보드가 도착하기 전에는 `board(player)`가 `null`일 수 있다. `landing()` 값은 `행 * 10 + 열`이다. 빈 칸은 `BoardSnapshot.EMPTY`, 회색 칸은 `CellCodes.GARBAGE`이며 보드 배열은 읽기 전용으로 취급한다.
- `lagging()`으로 랙을 표시한다. `roundTripMillis()`는 왕복 지연을 반환하며 첫 응답 전에는 `-1`이다. 연결이 끊기면 `IDLE`로 돌아오므로 P2P 첫 화면에서 `status()`를 표시한다. 사용자가 연결을 종료하거나 P2P 화면을 나갈 때도 `close()`를 호출한다.

## 테스트 담당

`gradlew.bat --offline --rerun-tasks check`를 실행해 기존 테스트의 회귀 여부를 확인한다. P2P를 별도로 검증할 때는 같은 PC의 루프백 주소 `127.0.0.1`에 호스트·클라이언트 세션을 연결할 수 있다. `com.team.tetris.p2p` 패키지의 테스트는 포트를 받는 생성자를 사용할 수 있다. 최근 IP 저장 검증은 별도 JVM에 테스트용 `-Duser.home`을 지정해 개발자의 접속 기록과 분리한다.

| 확인 항목 | 기대 결과 |
| --- | --- |
| 접속 성공·실패 | 정상 IP는 양쪽이 `LOBBY`로 전환된다. 잘못된 IP에는 오류를 표시하고 성공한 IP만 재사용할 수 있다. |
| 모드·준비·재대전 | 호스트의 모드가 양쪽에 반영된다. 한쪽만 준비하면 대기하고 양쪽이 준비하면 시작한다. 종료 후 준비 상태를 초기화하고 같은 연결에서 다시 시작한다. |
| 동기화·공격 | 한쪽의 키 입력이 상대 보드 상태에 반영된다. 받은 공격의 빈칸과 순서가 유지되고, 다음 블록 생성 전에 삽입된다. |
| 승패·시간제한 | 쌓을 수 없는 쪽이 패배한다. 3분 대전에서는 종료 시 고득점이 승리하고, 동점이면 무승부다. 결과는 양쪽에서 일치한다. |
| 느린 네트워크·끊김 | 응답이 지연되면 랙을 표시한다. 미응답이 계속되면 오류와 함께 `IDLE`로 돌아온다. 상대가 연결을 닫는 경우도 확인한다. |

11월 24일 실행에서 기존 테스트 410개와 별도 로컬 소켓 검증이 통과했다. 같은 PC에서 입력부터 상대 보드 데이터 갱신까지 마지막 측정값은 호스트→클라이언트 8ms, 클라이언트→호스트 6ms였다. **실제 두 PC의 동일 LAN에서 키 입력부터 상대 화면에 그려지기까지 200ms 이하**인지 UI 연결 후 양방향으로 측정해야 한다. 접속 자동화 테스트는 팀 테스트에 편입해야 커버리지 집계에 포함된다.

## 배틀 담당

[공통 연결 계약](battle/integration.md#1-공격-줄-데이터-구조)에 따라 양쪽 PC가 자기 `GameEngine`을 실행하고, 기존 `VersusMatch.standard()`로 엔진을 구성한다. `AttackLink.send()`는 공격을 소켓으로 보내며, 수신 처리는 받은 줄을 `GarbageQueue`에 넣는다. 이 대기열이 10줄 상한과 잘라내기를 처리한다. 기존 엔진은 현재 블록을 고정한 뒤 `AttackLink.takeIncoming()`으로 대기열을 비워 보드에 넣고 다음 블록을 생성한다.

- 공격 한 건은 위에서 아래 순서의 `List<GarbageLine>`이다. 왼쪽 열이 0이고 비트가 1인 칸이 채워진다. 소켓에서는 각 줄의 10비트 마스크를 2바이트 정수로 보낸다. 마스크의 모양과 줄 순서는 그대로 유지한다.
- 호스트는 경기 시작 시 모드와 난이도를 전달하고, 종료 시 승자와 양쪽 최종 점수를 담은 `MatchResult`를 확정한다. 시간제한은 공통 상수인 180초를 사용하며, 만료 시 최종 점수로 승패를 정한다.
- 랙·끊김 기준은 팀 확정 전이다. 현재는 왕복 500ms 이상 또는 1초 미응답에 랙을 표시하고, 5초 미응답 시 연결을 종료한다.

## 소스

- [P2pSession.java](../src/main/java/com/team/tetris/p2p/P2pSession.java)로 접속·경기 상태를 관리한다.
- [P2pConnection.java](../src/main/java/com/team/tetris/p2p/P2pConnection.java)에서 TCP 연결을 열고 메시지를 송수신한다.
- [P2pProtocol.java](../src/main/java/com/team/tetris/p2p/P2pProtocol.java)에서 보드·공격·결과 등의 전송 형식을 정의한다.
