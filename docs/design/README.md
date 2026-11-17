# SE Tetris — 뿌요풍 UI/UX 디자인 에셋

`docs/design/` 아래에 그대로 넣어 쓰는 디자인 에셋 폴더입니다.

## 구성
| 파일 | 용도 |
| --- | --- |
| `design-spec.md` | **소스 오브 트루스.** 컬러 토큰, 셀 렌더링 공식, 이펙트 프레임, 아이템 5종, 타이포, 제약 조건. AI(코파일럿/클로드)에게 그대로 던져도 되는 구현 명세 |
| `design-tokens.json` | 컬러 토큰만 뽑은 JSON. `ColorScheme` 교체 시 참조 |
| `mockups/*.png` | 전 화면 목업 18장 (@2x). 구현 결과 비교·검수용 참조 이미지 |

## 목업 목록
- 화면: MenuScreen, Game1P, VersusScreen, PauseScreen, GameOverScreen, NameInputScreen, ScoreboardScreen, SettingsScreen, BattleSelectScreen, P2PLobbyScreen
- 이펙트 순간: VersusAttack(공격), LineClearSteps(줄삭제 단계), FeverScreen, SlowScreen, BombScreen(폭탄+심지), AnvilScreen(무게추 분쇄)
- 레퍼런스: Main(디자인 소스), Components(컴포넌트 시트 — 셀/버튼/패널/대기열/이펙트 전부)

## 사용 규칙 (요약 — 상세는 design-spec.md §7)
1. 모든 수치는 셀 크기 `c`에서 파생 (radius = c×0.26 등) — 창 크기 S/M/L은 c만 변경
2. 이미지 에셋 없이 Graphics2D 드로잉이 기본. 필요 시 BufferedImage 캐싱은 재량 (스펙 §2.1)
3. 상시 애니메이션 루프 금지 — 모든 이펙트는 1~2프레임 (저사양 1.2GHz/1GB 대응)
4. SPEED 표기 금지, `engine.getLevel()` 기준 LEVEL로 통일
5. 색약 모드는 colorblind 팔레트로 토큰만 교체
