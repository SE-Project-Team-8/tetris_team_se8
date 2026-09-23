# block 개발 문서 — 담당자 1

- 작성일: 2026-09-22 / 갱신일: 2026-09-23
- 대상: `block`을 구현·수정·검증하는 담당자 1
- 작업 기준: `feat/block`, 기반 커밋 `8b38565` 이후 로컬 구현
- 요구사항: [1차 요구사항 정리](requirments1.md), [역할 분담](roles.md)
- 연동 담당자용 문서: [block 연동 계약](block_integration_contract.md)

이 문서는 내부 구조, 설계 이유, 구현 알고리즘, 예외 설계, 테스트와 개발 이력을 관리한다. 공개 API의 시그니처·ID·팔레트 값·호출 순서·예외 반환 계약은 **연동 계약 문서를 기준으로 관리**한다. API 변경 시 코드·테스트·계약을 함께 갱신하며, 두 문서에 동일한 계약 표를 중복 유지하지 않는다.

현재 프로덕션 타입 15개, 테스트 클래스 9개와 `BoardFixtures`를 구현했다. 로컬 검증은 완료했으며 실제 엔진·UI 연동은 별도다. 이번 문서 분리는 기존 개발 문서의 내용을 목적별로 재배치한 것이며 코드나 공용 계약의 동작을 변경하지 않는다.

## 1. 개발 범위와 요구사항 추적

계획 수립 당시 `block`에는 `package-info.java`만 있었다. 기존 공용 계약을 보존하면서 다음 기능을 구현했다. `R1 §번호`는 요구사항 문서의 절 번호다.

| 근거 | 담당자 1의 구현 책임 | 개발 검증 |
| --- | --- | --- |
| R1 §2.2 | 보드, 참조 7종, 동일 확률 생성, 완성 행 제거 | 실제 형태·보드 변이·난수 매핑 테스트 |
| R1 §2.4 | 이동·시계 방향 회전 후보, 충돌, 하드드롭 거리 | 모든 회전 상태와 경계·장애물 테스트 |
| R1 §2.5 | 엔진이 사용할 실제 낙하 거리·삭제 수 제공 | 도메인 조합 테스트. 점수 공식은 구현하지 않음 |
| R1 §2.2·2.6 | 기본·색맹 모드의 불변 색상 매핑 | 팔레트 검증·입력 복사·모드별 값 테스트 |
| R1 §2.8 | 생성 위치의 배치 가능 여부 제공 | 생성 불가를 정상 실패로 반환하는 테스트 |
| R1 §3.1·3.3 | Java 21 호환 로직, 테스트·커버리지 | Java 21 toolchain 빌드와 JaCoCo 확인 |

게임 루프·타이머·점수·현재 블록·미리보기 큐·입력·설정 저장·화면은 상위 모듈의 책임이다. 인프라·CI·배포·프로젝트 NFR은 D의 책임이다. 구체적인 담당 경계와 미완료 연동 항목은 연동 계약에 모았다.

요구사항이 허용한 API·자료구조 선택과, e-class 확인이 필요한 요구·평가 기준의 해석을 구분한다. 구현 승인이나 로컬 테스트 통과를 팀 전체의 합의 또는 e-class 답변으로 기록하지 않는다.

## 2. 실제 파일 구조

소스와 테스트 모두 `com.team.tetris.block` 패키지를 사용한다. 현재 규모에서는 접근 제한과 책임 분리로 경계를 만들며 별도 Gradle 모듈은 만들지 않는다.

```text
src/
├── main/java/com/team/tetris/block/
│   ├── package-info.java                   # 담당 범위·의존성 규칙
│   ├── Cell.java                           # 불변 좌표
│   ├── Rotation.java                       # 회전 상태
│   ├── TetrominoType.java                  # 종류 ID·형태 정의
│   ├── Tetromino.java                      # 불변 블록과 후보 계산
│   ├── BoardView.java                      # 읽기 전용 계약
│   ├── Board.java                          # 고정 셀 변경의 단일 소유자
│   ├── CollisionChecker.java               # 경계·점유 판단
│   ├── DropCalculator.java                 # 최대 낙하 거리
│   ├── LineClearer.java                    # 내부 완성 행 탐색
│   ├── TetrominoGenerator.java             # 생성 정책 경계
│   ├── UniformTetrominoGenerator.java      # 균등 독립 추출
│   ├── ColorScheme.java                    # 불변 팔레트
│   ├── InvalidTetrominoIdException.java
│   ├── InvalidColorSchemeException.java
│   └── InvalidPlacementException.java
└── test/java/com/team/tetris/block/
    ├── package-info.java
    ├── TetrominoTypeTest.java
    ├── TetrominoTest.java
    ├── BoardTest.java
    ├── CollisionCheckerTest.java
    ├── DropCalculatorTest.java
    ├── LineClearerTest.java
    ├── UniformTetrominoGeneratorTest.java
    ├── ColorSchemeTest.java
    ├── BlockFlowTest.java
    └── BoardFixtures.java
```

`Board`와 `LineClearer`를 같은 패키지에 두어 내부 행 탐색기를 패키지 전용으로 유지한다. Java 하위 패키지는 별도 패키지이므로 `internal` 디렉터리로 옮기는 것만으로 상위 패키지가 접근할 수 있는 것은 아니다. `TetrominoType`의 로컬 형태 조회도 패키지 전용이고, 외부에는 `Tetromino.cells()`를 제공한다.

프로덕션 타입은 외부 사용에 필요한 경우만 public이며 `LineClearer`, 테스트 클래스, `BoardFixtures`는 패키지 전용이다. 필드와 도우미는 가능한 한 private으로 제한한다. 보드 배열을 반환하거나 테스트 편의를 위한 공개 setter를 만들지 않는다.

## 3. 모듈화와 추상화 전략

### 3.1 책임과 상태 소유권

| 논리 모듈 | 타입 | 책임과 변경 이유 |
| --- | --- | --- |
| 형태·좌표 | `Cell`, `Rotation`, `TetrominoType`, `Tetromino` | 종류·형태·불변 후보 계산. 보드와 점수는 알지 못함 |
| 보드 상태 | `BoardView`, `Board` | 고정 셀의 읽기 경계와 상태 변경. 낙하 블록을 저장하지 않음 |
| 계산 | `CollisionChecker`, `DropCalculator`, `LineClearer` | 조회만으로 충돌·거리·완성 행 계산 |
| 생성 | `TetrominoGenerator`, `UniformTetrominoGenerator` | 난수 표본을 종류로 변환. 위치·큐는 관리하지 않음 |
| 표현 데이터 | `ColorScheme` | 팔레트 완전성과 불변성. UI 객체·설정 저장에 의존하지 않음 |
| 실패 문맥 | 커스텀 예외 3종 | 계약 위반의 의미와 작은 값만 보관. 로깅·복구를 하지 않음 |

의존 방향:

- `Board` → `CollisionChecker`, `LineClearer` → `BoardView` 조회.
- `DropCalculator` → `CollisionChecker`, `BoardView`, `Tetromino`.
- `Tetromino` → `TetrominoType`, `Rotation`, `Cell`.
- `UniformTetrominoGenerator` → `TetrominoType`, 주입된 `RandomGenerator`.
- `ColorScheme` → `TetrominoType`. 충돌·보드 로직은 팔레트에 의존하지 않는다.
- 기본 보드 크기만 `common.constants.GameConstants`를 사용한다.

`game`, `ui`, `settings`, `scoreboard` import는 금지한다. Swing/AWT·파일·타이머·전역 변경 상태도 도메인 계산에 넣지 않는다. 공용 DTO 변환과 이벤트 발행은 엔진이 수행한다.

### 3.2 필요한 경계만 추상화

- `BoardView`는 계산기에 조회 권한만 제공한다. 구현을 `Board`로 다운캐스팅하지 않는다. 실시간 조회이므로 스냅샷·동시성 보장으로 해석하지 않는다.
- `TetrominoGenerator`는 종류 공급 정책을 교체하는 경계다. 구현은 non-null 종류를 반환하고 숨은 큐·점수 변경이나 추가 호출 순서를 요구하지 않는다.
- 난수원은 생성자로 주입해 결과를 재현할 수 있게 한다. 생성기의 상태 독립이 필요하면 같은 변경 가능한 난수원을 공유하지 않는다.
- 두 팔레트는 동작보다 데이터의 차이이므로 불변 `ColorScheme` 인스턴스로 표현한다. 선택된 모드를 변경 가능한 static 필드에 저장하지 않는다.
- 상태 없는 계산기에 일괄적으로 인터페이스를 만들지 않는다. 실제 대안이 생길 때 회전·충돌 정책 경계를 추출한다.
- `AbstractTetromino`, 일곱 형태별 하위 클래스, 범용 `BlockService`, 서비스 로케이터·DI 프레임워크는 현재 필요하지 않다.

새 세션의 보드·생성기와 현재 블록의 수명은 엔진이 조립한다. 도메인은 직렬 호출을 전제로 하며 임의의 잠금·스레드로 소유권 문제를 숨기지 않는다.

### 3.3 SOLID 적용

| 원칙 | 적용 |
| --- | --- |
| SRP | 형태, 상태 변경, 생성, 색상, 게임 진행의 변경 이유를 분리 |
| OCP | 생성 정책은 구현 교체로 확장. enum 종류 추가 시 영향 범위는 명시적으로 검토 |
| LSP | 읽기 뷰·생성기 구현 간 입력·반환·예외 의미와 부작용 계약 유지 |
| ISP | 계산기에 쓰기·이벤트·점수 기능이 포함되지 않은 조회 인터페이스 제공 |
| DIP | 계산은 `BoardView`, 생성은 `TetrominoGenerator`·`RandomGenerator` 경계 사용 |

### 3.4 변경 영향과 확장 기준

| 변경 | 수정 위치 | 확인 사항 |
| --- | --- | --- |
| 생성 규칙 | 생성기 구현 | 새 요구와 선택 조건, 엔진 큐 계약 |
| 팔레트 | `ColorScheme` 데이터·검증 | 종류 ID 유지, 실제 UI 식별성 |
| 회전·벽 차기 | 후보 계산, 필요 시 정책 추출 | 원점·기존 회전·충돌·불변성 |
| 새 블록 종류 | 종류·형태·생성 목록·팔레트·ID | 네 셀·일곱 종류 가정과 엔진·UI 영향 |
| 화면 크기 | 상위 UI | 논리 보드 크기 유지 |
| 공용 DTO | 공용 계약 소유자와 엔진 변환 | 배열 소유권과 소비자 호환성 |

새 종류 추가는 현재 enum 설계에서 여러 파일 변경이 필요한 작업이다. 이를 숨기지 않고 누락을 테스트로 찾는다. 폴더 이름만으로 `model/service/util`을 나누지 않으며 실제 변경 단위·접근 경계가 커질 때 패키지를 추출한다.

## 4. 알고리즘과 내부 불변식

### 4.1 형태·회전

초기 형태는 [참조 저장소의 고정 커밋](https://github.com/Jindae/SeoulTech-SE-Tetris-Ref/tree/2e9ce46ae447f37475489687efae87ab74da084a/src/seoultech/se/tetris/blocks)과 맞춘다. 기존 참조 코드의 상속 구조는 채택하지 않고 enum의 형태 데이터로 표현했다.

enum 초기화에서 네 회전의 로컬 좌표 목록을 계산해 불변 목록으로 보관한다. 행이 아래로 증가하는 좌표계에서 `(r,c) → (c,N-1-r)`을 사용한다. 고정 격자와 O의 점유 위치를 유지하며 회전마다 점유 영역을 잘라 원점을 재설정하지 않는다. 정확한 원점·초기 모양·회전 상태는 연동 계약에 명시한다.

모든 형태는 중복 없는 네 셀이다. `Tetromino` 생성 시 `Math.addExact`로 절대 셀의 표현 가능성을 확인한다. 음수 좌표는 허용하지만 정수 오버플로가 반대편 위치로 바뀌는 것은 거부한다. 유효하게 생성된 값의 `cells()`는 불변 목록을 반환하고, 이동·회전은 원본을 변경하지 않는다.

### 4.2 보드·충돌·배치

내부 배열은 `TetrominoType[][]`이며 null은 내부 빈 칸 표현이다. 공개 조회는 `Optional`로 변환한다. `CollisionChecker`는 범위를 확인한 뒤 셀을 조회하므로 정상적인 경계 충돌이 조회 예외로 바뀌지 않는다.

`Board.tryPlace()`는 셀 목록을 한 번 계산하고 패키지 전용 `canPlaceCells()`로 네 셀 전체를 검사한다. 검사 후 같은 목록을 기록하며 중간에 외부 호출·좌표 재계산을 넣지 않는다. 따라서 정상 거절 시 부분 기록이 없다. 착지 여부·고정 시점은 엔진의 책임이다.

### 4.3 행 삭제

`LineClearer.findFullRows()`는 완성 행을 오름차순 불변 목록으로 반환한다. `Board`는 아래부터 생존 행을 새 배열에 복사하고 위쪽을 비운 뒤 내부 배열 참조를 교체한다. 탐색과 실제 변경을 분리하되 완성 행 판정 알고리즘을 중복 구현하지 않는다.

새 행끼리도 배열을 공유하지 않는다. 삭제할 행이 없으면 새 배열을 만들지 않는다. 연속·비연속 완성 행을 모두 처리하며 삭제 수를 4로 제한하지 않는다. 처리 시간·추가 공간은 `O(rows × cols)`다.

### 4.4 하드드롭·생성

하드드롭은 시작 배치의 유효성을 확인한 뒤 한 칸씩 후보를 내려 첫 충돌 직전 거리를 반환한다. 중간 장애물 아래의 빈 공간으로 통과하지 않는다. 네 셀 기준 시간은 `O(rows)`이며 입력 보드와 블록을 변경하지 않는다.

생성기는 `nextInt(종류 수)`의 표본 한 개를 종류 한 개에 대응시킨다. 이력·재추첨·가방을 두지 않는다. 초기 계획의 7-bag는 이전 결과에 따라 다음 선택의 조건부 확률이 달라지므로 R1 구현에서 제외했다. 난수 예외를 대체 블록으로 숨기지 않는다.

### 4.5 팔레트

입력 맵을 복사하고 null 항목, 완전성, RGB 범위, 종류 및 빈 칸과의 중복을 검증한 뒤 불변 맵으로 보관한다. 조회마다 전체 검증을 반복하지 않는다.

색맹 보조 팔레트는 [Okabe·Ito의 Color Universal Design](https://jfly.uni-koeln.de/color/)을 참고했다. R1의 NULI 링크는 확인 당시 502로 읽지 못했다. 실제 값과 UI의 문자·무늬 및 색각 검증 책임은 연동 계약에 기록한다. RGB 유일성만으로 시각적 식별성을 입증하지 않는다.

## 5. 예외를 추가·변경하는 개발 전략

공개 API별 반환·예외 표는 연동 계약에서 관리한다. 이 절은 예외를 선택하고 구현하는 기준이다.

| 분류 | 구현 원칙 |
| --- | --- |
| 정상 게임 실패 | 충돌은 false, 이미 착지·삭제할 행 없음은 0. 정상 종료도 예외로 만들지 않음 |
| 호출 계약 위반 | null·잘못된 크기·직접 조회 좌표·ID·팔레트·무효 드롭 시작을 구체적인 unchecked 예외로 표현 |
| 내부 불변식 위반 | 우선 테스트로 발견. 런타임 검사에서 발견하면 `IllegalStateException` 등으로 결함을 드러냄 |
| 외부 I/O | 현재 도메인에는 없음. 해당 저장소의 계약으로 처리 |
| JVM `Error` | 도메인에서 잡거나 정상 결과로 변환하지 않음 |

### 5.1 커스텀 예외 기준

현재 `InvalidTetrominoIdException`, `InvalidColorSchemeException`, `InvalidPlacementException`은 `IllegalArgumentException`의 하위 타입이다. 도메인 의미와 실패 문맥을 구별할 수 있게 하되 모든 예외를 `BlockException`으로 감싸지 않는다.

`BlockInvariantViolationException extends IllegalStateException`은 조건부 후보이며 아직 없다. 서로 다른 내부 검사에서 같은 결함을 구분해 처리할 필요가 생길 때 도입한다. `GameOverException`, `CollisionException`, `NoLinesToClearException`도 만들지 않는다. 현재 복구 가능한 외부 작업이 없으므로 checked 예외·`throws Exception`을 추가하지 않는다.

예외에는 ID·종류·회전·좌표·크기 같은 작은 값을 저장한다. `InvalidPlacementException`은 보드나 배열을 참조하지 않고 스칼라·enum 문맥으로 `piece()` 값을 복원한다. 팔레트 위반 이유별로 실제 복구가 달라지는 요구가 생기면 이유 enum을 추가하고 메시지 파싱은 사용하지 않는다.

### 5.2 검증 순서·상태 보존

1. 필수 참조를 `Objects.requireNonNull`로 검사한다.
2. 데이터 소유자에서 인자 형식·범위·완전성을 검증한다.
3. 셀 목록·충돌·압축 결과를 계산한다.
4. 결과가 완성된 뒤 쓰기를 확정한다.

공개 인자 검증에 비활성화될 수 있는 `assert`를 사용하지 않는다. 원자적 배치와 별도 배열에서의 행 압축으로 예상 가능한 실패의 상태 보존을 지킨다. JVM 실패·프로세스 종료·지원하지 않는 동시 수정까지 롤백을 보장하지 않는다. 엔진의 고정→삭제→점수→이벤트 전체는 도메인의 단일 트랜잭션이 아니다.

형태·ID·내장 팔레트의 불변식을 데이터 테스트로 검사한다. 정적 초기화에서 실패하면 JVM 초기화 오류로 드러날 수 있으므로 게임 중 일반 복구 대상으로 취급하지 않는다.

### 5.3 메시지·전파·로깅

- 메시지에는 연산, 잘못된 값, 기대 조건을 담고 전체 문자열을 호환성 계약으로 고정하지 않는다.
- 예외 변환이 필요한 실제 경계에서만 변환하고 원본 `cause`를 보존한다.
- `catch (Exception)`으로 false/0/null을 반환하거나 실패를 삼키지 않는다.
- `catch (Throwable)`·`catch (Error)`로 JVM 실패를 정상 흐름에 넣지 않는다. `finally`에서 원래 예외를 가리지 않는다.
- 도메인은 로그·대화상자·`System.exit()`를 호출하지 않는다. 최종 처리 경계가 한 번 기록한다.
- 엔진의 중단·재시작·오류 전달은 연동 계약의 처리 책임을 따른다.

## 6. 테스트 구성과 품질 기준

기존 JUnit Jupiter를 사용하며 새 테스트 프레임워크·빌드 소스 세트는 추가하지 않았다. 기대값은 명시적인 좌표·보드로 적고 구현 알고리즘을 테스트에서 복제하지 않는다.

| 테스트 파일 | 핵심 검증 |
| --- | --- |
| `TetrominoTypeTest` | 종류 집합, 명시적 ID 왕복, 빈 칸 포함 잘못된 ID와 예외 문맥 |
| `TetrominoTest` | 참조 7종 × 4회전의 기대 좌표, 이동, 네 번 회전 복원, O 유지, 불변성, null·오버플로 |
| `BoardTest` | 기본·작은 보드, 크기·조회 오류, 배치 원자성, 0~4줄 및 그 이상 삭제, 비연속 행·생존 순서·행 공유 방지, 초기화 |
| `CollisionCheckerTest` | 네 경계, 모든 회전의 점유 충돌, 실제 점유 셀과 기준점 구분, 다른 읽기 뷰, null |
| `DropCalculatorTest` | 모든 종류·회전의 착지, 장애물 통과 금지, 거리 0, 시작 오류·문맥·무변경 |
| `LineClearerTest` | 오름차순 완성 행 목록, 불변 반환, 비완성·작은 보드 |
| `UniformTetrominoGeneratorTest` | 표본의 일대일 대응, 중복 허용, 재추첨 없음, 재현성·인스턴스 독립, 난수 실패 전파 |
| `ColorSchemeTest` | 두 팔레트의 완전성·전환, 복사, null·누락·중복·RGB 범위·배경 중복 |
| `BlockFlowTest` | 실제 객체로 생성→이동→착지→고정→삭제, 생성 불가, 회전 거절 |

`Cell`·`Rotation`은 블록 동작 테스트, 커스텀 예외는 실제로 던지는 API 테스트에 포함한다. getter·record 자체만 검증하려고 별도 테스트 클래스를 만들지 않는다.

`BoardFixtures`는 여러 테스트의 준비·비교를 공유한다. 실제 `Board`는 공개 배치 API로 준비하고 내부 배열을 직접 수정하지 않는다. 읽기 전용 테스트 보드는 입력을 복사한다. 한 테스트만 사용하는 제어 난수원은 해당 테스트의 private 중첩 타입이다.

### 6.1 실패 테스트 원칙

- 예외의 구체 타입 또는 의도한 부모 계약과 실패 문맥을 검증한다. 넓은 `Exception.class`와 전체 메시지 문자열 비교는 피한다.
- false 반환과 예외 발생을 구분하고 실패 전후 전체 보드를 비교한다.
- 난수 빈도가 유한 표본에서 정확히 같아야 한다고 가정하지 않는다. 임의 통계 임계값을 CI 조건으로 두지 않는다.
- 실제 `OutOfMemoryError`를 유발하거나 테스트용 공개 setter를 추가하지 않는다.
- 엔진의 점수·스냅샷·입력·기술적 실패 테스트와 UI 접근성 검증은 타 담당자의 연동 범위다.

### 6.2 실행 명령과 기준

```bash
./gradlew test --tests 'com.team.tetris.block.*'
./gradlew build javadoc
git diff --check
```

Windows에서는 `gradlew.bat`를 사용한다. R1 §3.3의 50~70% 이상 표현에 대해 `block` 목표를 70% 이상으로 잡았다. 정확한 전체 평가 기준·산정 범위는 D와 확인하며, 모호한 의미는 e-class 확인 대상으로 남긴다.

현재 `jacocoTestCoverageVerification`은 `minimum = 0.50`이지만 비활성이다. 빌드 성공만으로 커버리지 통과를 선언하지 않고 보고서를 확인한다. 해당 설정 변경은 D의 범위다. 비활성 NFR 자리표시자를 실제 NFR 검증으로 세지 않는다.

## 7. 구현 이력과 확인된 결과

| 단계 | 결과 |
| --- | --- |
| 계약·범위 분석 | 스캐폴드·공용 계약·R1을 확인하고 기능과 미확정 연동을 분리 |
| 값 모델 | 불변 좌표·회전·종류·블록, ID 예외 구현 |
| 보드·계산 | 상태 캡슐화, 충돌, 드롭, 시작 배치 예외 구현 |
| 행 삭제 | 내부 탐색과 새 배열 압축 구현 |
| 생성·색상 | 균등 생성, 두 팔레트, 검증 예외 구현 |
| 검증·주석 | 테스트·전체 빌드·Javadoc 검증 완료, 내부 동작 이유와 API 계약 주석 보강 |
| 문서 분리 | 개발 내용은 이 문서, 외부 연결 사항은 연동 계약으로 분리 |

2026-09-23 코드 구현 후 실행한 결과다. 문서 분리 작업에서 테스트를 새로 실행한 결과로 표현하지 않는다.

| 항목 | 결과 |
| --- | --- |
| block 테스트 | 121개 통과, 실패·오류·건너뜀 0 |
| 전체 빌드·Javadoc | `./gradlew build javadoc` 성공 |
| block 라인 커버리지 | 193/193, 100% |
| block 분기 커버리지 | 72/72, 100% |
| 주석 경고 | block 0개. 기존 `Main`·`common` 21개 |
| 환경 | macOS 26, Java 21 toolchain. Windows 11은 미검증 |

결과 파일:

- 테스트: `build/reports/tests/test/index.html`
- 커버리지: `build/reports/jacoco/test/html/index.html`, `build/reports/jacoco/test/jacocoTestReport.xml`
- API 문서: `build/docs/javadoc/index.html`

커버리지는 JaCoCo가 집계한 block 패키지의 값이며 전체 프로젝트나 가능한 모든 입력의 검증을 뜻하지 않는다. 코드 구현·문서 분리 과정에서 `common`·타 담당 소스·빌드·CI는 변경하지 않았다. 커밋·푸시도 수행하지 않았다.

## 8. 담당자 1의 유지보수 체크리스트

- [x] 기본 기능·불변 값·상태 소유권·예외 전략을 구현했다.
- [x] 계획한 타입·테스트 파일 구조를 작성했다.
- [x] 경계·실패·회전·압축·드롭·난수·팔레트 테스트를 통과했다.
- [x] 금지 import와 내부 배열 노출을 점검했다.
- [x] 주석·Javadoc과 실제 코드 계약을 맞췄다.
- [x] 개발 결과와 외부 연동 계약의 문서를 분리했다.
- [ ] 실제 엔진·UI 연결 과정에서 발견한 불일치를 재현 테스트와 함께 반영한다.
- [ ] API·ID·좌표·예외가 바뀌면 영향받는 담당자와 계약 문서를 함께 갱신한다.

팀별 미완료 작업과 합의 항목은 [연동 계약의 확인 목록](block_integration_contract.md#8-연동-확인-목록)에서 관리한다.
