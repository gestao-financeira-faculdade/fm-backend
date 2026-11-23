package com.emanudev.gestaofinanceira.shared.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

    @Test
    @DisplayName("DEF-21: página gigante não estoura int no offset")
    void shouldComputeOffsetWithoutIntOverflow() {
        assertThat(PageQuery.of(Integer.MAX_VALUE, 500).offset()).isEqualTo((long) Integer.MAX_VALUE * 500);
    }
}
