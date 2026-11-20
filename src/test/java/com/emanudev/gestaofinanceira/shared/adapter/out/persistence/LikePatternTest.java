package com.emanudev.gestaofinanceira.shared.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternTest {

    @Test
    @DisplayName("DEF-42: % e _ da busca viram literais")
    void shouldEscapeWildcards() {
        assertThat(LikePattern.escape("100%_off")).isEqualTo("100\\%\\_off");
        assertThat(LikePattern.escape("c:\\pasta")).isEqualTo("c:\\\\pasta");
        assertThat(LikePattern.escape("fatura")).isEqualTo("fatura");
    }
}
