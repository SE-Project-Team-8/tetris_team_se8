# tetris_team_se8

Java 21 + Gradle 기반 Swing 텍스트 테트리스 팀 프로젝트입니다.

담당자 2의 게임 진행·점수·설정·순위 저장 모듈과 테스트를 구현했습니다.
블록 도메인과 게임 엔진은 `BlockBoardDriver`로 연결했고, `Main`이 화면·설정·순위 저장소를 조립합니다.
`./gradlew run`으로 게임을 시작할 수 있습니다.
구현 규칙과 연결 API는 [담당자 2 연동 문서](docs/person2-integration.md)를 참고하세요.

## 코드 파일 구조

```text
src/
├── main/java/com/team/tetris/
│   ├── Main.java                                      # B
│   │
│   ├── block/                                         # 담당자1
│   │   ├── package-info.java                          # 담당자1
│   │   ├── Board.java, BoardView.java, Cell.java       # 보드/셀
│   │   ├── Tetromino.java, TetrominoType.java          # 블록과 7종 타입
│   │   ├── Rotation.java, TetrominoGenerator.java      # 회전/생성 계약
│   │   ├── UniformTetrominoGenerator.java             # 기본 생성기
│   │   ├── CollisionChecker.java, DropCalculator.java # 충돌/낙하
│   │   ├── LineClearer.java, ColorScheme.java          # 줄 삭제/색상
│   │   ├── InvalidTetrominoIdException.java          # 잘못된 블록 ID
│   │   ├── InvalidPlacementException.java            # 잘못된 배치
│   │   └── InvalidColorSchemeException.java          # 잘못된 색상 모드
│   │
│   ├── game/                                          # 담당자2
│   │   ├── package-info.java                          # 담당자2
│   │   ├── GameEngine.java                            # 담당자2 (구현)
│   │   ├── BlockBoardDriver.java                      # block ↔ game 어댑터
│   │   ├── GameAction.java                            # 키 입력과 독립적인 게임 동작
│   │   ├── GameState.java                             # 담당자2 (구현)
│   │   ├── ScoreCalculator.java                       # 기본 점수 정책
│   │   ├── ScoringPolicy.java                         # 교체 가능한 점수 규칙
│   │   ├── SpeedPolicy.java                           # 교체 가능한 낙하 속도 규칙
│   │   └── DefaultSpeedPolicy.java                    # 1차 속도 정책
│   │
│   ├── settings/                                      # 담당자2
│   │   ├── package-info.java                          # 담당자2
│   │   ├── GameSettings.java                          # 담당자2 (구현)
│   │   ├── SettingsRepository.java                    # 담당자2 (구현)
│   │   └── KeyBindings.java                           # 담당자2 (구현)
│   │
│   ├── scoreboard/                                    # 담당자2
│   │   ├── package-info.java                          # 담당자2
│   │   ├── ScoreRecord.java                           # 담당자2 (구현)
│   │   └── ScoreboardRepository.java                  # 담당자2 (구현)
│   │
│   ├── ui/                                            # B
│   │   ├── package-info.java                          # B
│   │   ├── ScreenRouter.java                          # 화면 전환·창 크기
│   │   ├── MainMenuScreen.java                        # 시작 메뉴
│   │   ├── GameScreen.java                            # 게임 보드·입력
│   │   ├── PauseScreen.java                           # 일시정지·종료
│   │   ├── GameOverScreen.java                        # 게임 결과
│   │   ├── NameInputScreen.java                       # 순위권 이름 입력
│   │   ├── SettingsScreen.java                        # 크기·색상·키 설정
│   │   └── ScoreboardScreen.java                      # 저장된 점수 표시
│   │
│   └── common/                                        # 담당자2 + B (공용 계약)
│       ├── package-info.java                          # 담당자2 + B
│       ├── Screen.java                                # 담당자2 + B
│       ├── persistence/PropertiesFile.java            # 담당자2 (UTF-8 저장)
│       ├── constants/
│       │   ├── GameConstants.java                     # 담당자2 + B
│       │   └── package-info.java                      # 담당자2 + B
│       └── events/
│           ├── BoardSnapshot.java                     # 담당자2 + B
│           ├── GameEventListener.java                 # 담당자2 + B
│           └── package-info.java                      # 담당자2 + B
│
└── test/java/com/team/tetris/
	├── block/package-info.java                        # 담당자1
	├── game/package-info.java                         # 담당자2
	├── settings/package-info.java                     # 담당자2
	├── scoreboard/package-info.java                   # 담당자2
	└── nfr/
		└── NonFunctionalRequirementsTest.java         # D

프로젝트 설정 및 협업 파일
├── build.gradle                                       # D
├── gradle.properties                                  # 한글 경로 빌드 인코딩
├── settings.gradle                                    # D
├── gradlew                                            # D
├── gradlew.bat                                        # D
├── gradle/wrapper/gradle-wrapper.jar                  # D
├── gradle/wrapper/gradle-wrapper.properties           # D
├── .github/workflows/ci.yml                           # D
├── .github/PULL_REQUEST_TEMPLATE.md                   # D
├── .github/ISSUE_TEMPLATE/interface-mismatch.md       # D
├── docs/roles.md                                      # D
└── README.md                                          # D
```

위 구조는 현재 구현한 주요 파일을 보여줍니다. `package-info.java`는 다른 Java 파일과 같은 깊이에 있습니다.

## 역할 요약

| 담당자 | 담당 영역 |
| --- | --- |
| 담당자1 | `block` 순수 게임 로직 |
| 담당자2 | `game`, `settings`, `scoreboard`, `SettingsScreen`, `ScoreboardScreen` |
| B | 나머지 `ui` 화면 및 `ScreenRouter` |
| D | 인프라, CI, 배포(`jpackage`), NFR 테스트 |

마감: 9/23

## 빌드

```bash
./gradlew clean build
```

Windows PowerShell에서는 `.\gradlew.bat clean build`를 실행합니다.
실행은 `.\gradlew.bat run`입니다. 기본 조작은 방향키 이동·회전, Space 하드 드롭,
P 일시정지·재개, Esc 게임 종료입니다. 게임 설정 화면에서 키를 바꿀 수 있습니다.
`gradle.properties`의 `-Dfile.encoding=COMPAT`은 Java 21에서 Windows 한글 체크아웃 경로의
Gradle 테스트 실행 인수 파일을 시스템 문자셋으로 읽도록 하기 위한 설정입니다. macOS/Linux의
일반적인 UTF-8 환경에서는 동작에 영향이 없으며, 소스와 저장 데이터는 별도로 UTF-8을 사용합니다.
테스트 결과는 `build/reports/tests/test/index.html`, 커버리지는
`build/reports/jacoco/test/html/index.html`에서 확인할 수 있습니다.
