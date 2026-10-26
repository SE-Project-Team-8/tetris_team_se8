package com.team.tetris.game.item;

/**
 * 아이템 모드에서 10줄마다 등장하는 아이템의 공통 계약.
 *
 * 2차 요구사항 4.2의 두 형태를 하위 타입으로 나눈다. 기본 블록의 한 칸에 붙는 {@link EmbeddedItem},
 * 블록 자체가 아이템인 {@link StandaloneItem}. 봉인(sealed)되어 있어 두 형태를 구분하는 switch가
 * 빠짐없이 처리하는지 컴파일러가 검사한다. 새 아이템은 둘 중 하나를 구현하고
 * {@link ItemRegistry#defaults()}에 등록하면 되며, 드라이버·엔진·화면은 바뀌지 않는다.
 */
public sealed interface Item permits EmbeddedItem, StandaloneItem {
    /** 식별용 이름. 레지스트리 안에서 유일해야 한다. */
    String name();

    /**
     * 보드와 미리보기에 그리는 문자. 레지스트리 안에서 유일해야 하며(4.2) 셀 코드에 그대로 들어간다.
     * 색맹 팔레트에서도 문자는 유지되므로 색과 무관하게 아이템을 구분할 수 있다.
     */
    char symbol();
}
