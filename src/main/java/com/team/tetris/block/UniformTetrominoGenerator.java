package com.team.tetris.block;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 매 호출마다 7종을 각각 1/7 확률로 독립 선택하는 생성기.
 *
 * 이전 결과를 배제하거나 다시 뽑지 않는다. 7-bag와 달리 같은 종류가 연속해서 나올 수
 * 있으며 이는 요구한 확률 모델의 정상 결과다. 엔진은 세션별 난수원을 주입하고 호출을
 * 직렬화한다. 같은 난수원 객체를 공유한 생성기들의 상태 독립은 보장하지 않는다.
 */
public final class UniformTetrominoGenerator implements TetrominoGenerator {
    private static final List<TetrominoType> TYPES = List.of(TetrominoType.values());
    private final RandomGenerator random;

    /**
     * 엔진 또는 테스트가 소유한 난수원을 주입받아 생성 정책을 준비한다.
     *
     * @param random 종류 선택에 사용할 난수원. 고정 시드를 사용하면 테스트를 재현할 수 있다.
     * @throws NullPointerException 난수원이 null인 경우
     */
    public UniformTetrominoGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * 균등한 정수 표본 하나를 종류 하나에 대응시킨다.
     * 난수원 실패는 전파하며 임의의 블록을 대신 반환하지 않는다.
     *
     * @return 선택한 종류
     */
    @Override
    public TetrominoType next() {
        return TYPES.get(random.nextInt(TYPES.size()));
    }
}
