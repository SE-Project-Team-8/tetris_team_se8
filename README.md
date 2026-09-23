# tetris_team_se8

Java 21 + Gradle 기반 Swing 텍스트 테트리스 팀 프로젝트입니다.

담당자 2의 게임 진행·점수·설정·순위 저장 모듈과 테스트를 구현했습니다.
현재 체크아웃의 블록과 화면은 뼈대이며, 실제 게임 실행에는 팀원 모듈 연결이 필요합니다.
구현 규칙과 연결 API는 [담당자 2 연동 문서](docs/person2-integration.md)를 참고하세요.

## 코드 파일 구조

```text
src/
├── main/java/com/team/tetris/
│   ├── Main.java                                      # B
│   │
│   ├── block/                                         # 담당자1
│   │   ├── package-info.java                          # 담당자1
│   │   ├── Board.java                                 # 담당자1 (추가 예정)
│   │   ├── Tetromino.java                             # 담당자1 (추가 예정, 7종)
│   │   ├── TetrominoGenerator.java                    # 담당자1 (추가 예정)
│   │   ├── CollisionChecker.java                      # 담당자1 (추가 예정)
│   │   ├── LineClearer.java                           # 담당자1 (추가 예정)
│   │   └── ColorScheme.java                           # 담당자1 (추가 예정)
│   │
│   ├── game/                                          # 담당자2
│   │   ├── package-info.java                          # 담당자2
│   │   ├── GameEngine.java                            # 담당자2 (구현)
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
│   │   ├── ScreenRouter.java                          # B (추가 예정)
│   │   ├── MainMenuScreen.java                        # B (추가 예정)
│   │   ├── GameScreen.java                            # B (추가 예정)
│   │   ├── PauseScreen.java                           # B (추가 예정)
│   │   ├── GameOverScreen.java                        # B (추가 예정)
│   │   ├── NameInputScreen.java                       # B (추가 예정)
│   │   ├── SettingsScreen.java                        # 담당자2 (추가 예정)
│   │   └── ScoreboardScreen.java                      # 담당자2 (추가 예정)
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

위 구조는 현재 구현한 파일과 계획된 파일을 함께 보여줍니다. `(추가 예정)` 파일은 현재 체크아웃에 없으며,
`package-info.java`는 다른 Java 파일과 같은 깊이에 있습니다. 담당자는 각 패키지의 import 규칙을 지켜야 합니다.

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
테스트 결과는 `build/reports/tests/test/index.html`, 커버리지는
`build/reports/jacoco/test/html/index.html`에서 확인할 수 있습니다.
