# tetris_team_se8

Java 21 + Gradle 기반 Swing 텍스트 테트리스 팀 프로젝트입니다.

현재 저장소는 프로젝트 뼈대와 `common` 공용 계약만 포함합니다. 게임 로직과 실제 화면은 담당자가 구현할 예정입니다.

## 코드 파일 구조

```text
src/
├── main/java/com/team/tetris/
│   ├── Main.java                                      # B
│   │
│   ├── block/                                         # 담당자1
│   │   └── package-info.java                          # 담당자1
│   │       ├── Board.java                             # 담당자1 (추가 예정)
│   │       ├── Tetromino.java                         # 담당자1 (추가 예정, 7종)
│   │       ├── TetrominoGenerator.java                # 담당자1 (추가 예정)
│   │       ├── CollisionChecker.java                  # 담당자1 (추가 예정)
│   │       ├── LineClearer.java                       # 담당자1 (추가 예정)
│   │       └── ColorScheme.java                       # 담당자1 (추가 예정)
│   │
│   ├── game/                                          # 담당자2
│   │   └── package-info.java                          # 담당자2
│   │       ├── GameEngine.java                        # 담당자2 (추가 예정)
│   │       ├── GameState.java                         # 담당자2 (추가 예정)
│   │       └── ScoreCalculator.java                   # 담당자2 (추가 예정)
│   │
│   ├── settings/                                      # 담당자2
│   │   └── package-info.java                          # 담당자2
│   │       ├── GameSettings.java                      # 담당자2 (추가 예정)
│   │       ├── SettingsRepository.java                # 담당자2 (추가 예정)
│   │       └── KeyBindings.java                       # 담당자2 (추가 예정)
│   │
│   ├── scoreboard/                                    # 담당자2
│   │   └── package-info.java                          # 담당자2
│   │       ├── ScoreRecord.java                       # 담당자2 (추가 예정)
│   │       └── ScoreboardRepository.java              # 담당자2 (추가 예정)
│   │
│   ├── ui/                                            # B
│   │   └── package-info.java                          # B
│   │       ├── ScreenRouter.java                      # B (추가 예정)
│   │       ├── MainMenuScreen.java                    # B (추가 예정)
│   │       ├── GameScreen.java                        # B (추가 예정)
│   │       ├── PauseScreen.java                       # B (추가 예정)
│   │       ├── GameOverScreen.java                    # B (추가 예정)
│   │       ├── NameInputScreen.java                   # B (추가 예정)
│   │       ├── SettingsScreen.java                    # 담당자2 (추가 예정)
│   │       └── ScoreboardScreen.java                  # 담당자2 (추가 예정)
│   │
│   └── common/                                        # 담당자2 + B (공용 계약)
│       ├── package-info.java                          # 담당자2 + B
│       ├── Screen.java                                # 담당자2 + B
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

`(추가 예정)` 파일은 현재 구현하지 않은 담당 영역입니다. 담당자는 해당 패키지의 `package-info.java`에 적힌 import 규칙을 지켜야 합니다.

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
