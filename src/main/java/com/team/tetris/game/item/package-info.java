/**
 * 아이템 모드의 아이템과 낙하 조각. 아이템 계약({@link com.team.tetris.game.item.Item}과 두 형태),
 * 등록 목록({@link com.team.tetris.game.item.ItemRegistry}), 등장 규칙, 선택기, 낙하 조각 계약
 * ({@link com.team.tetris.game.item.FallingPiece}), 그리고 L·무게추 구현이 있다.
 *
 * 새 아이템은 이 패키지에 클래스 하나를 만들고 {@code ItemRegistry.defaults()}에 등록한다.
 * 절차와 효과 종류별 붙는 자리는 docs/items/README.md를 본다.
 * block과 common만 import하며 game의 다른 클래스나 ui를 import하지 않는다.
 */
package com.team.tetris.game.item;
