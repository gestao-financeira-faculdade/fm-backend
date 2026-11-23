package com.emanudev.gestaofinanceira.reference.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxIdTest {

    @Test
    @DisplayName("DEF-11: o mesmo documento com e sem máscara vira o mesmo valor")
    void shouldNormalizeMaskedAndUnmaskedToDigits() {
        assertThat(TaxId.normalizeAndValidate("12.345.678/0001-95")).isEqualTo("12345678000195");
        assertThat(TaxId.normalizeAndValidate("12345678000195")).isEqualTo("12345678000195");
        assertThat(TaxId.normalizeAndValidate("529.982.247-25")).isEqualTo("52998224725");
    }

    @Test
    @DisplayName("Documento é opcional: vazio vira null")
    void shouldTreatBlankAsAbsent() {
        assertThat(TaxId.normalizeAndValidate(null)).isNull();
        assertThat(TaxId.normalizeAndValidate("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"111.111.111-11", "abc", "529.982.247-24", "12.345.678/0001-90", "1234567890", "QA-12345678000195"})
    @DisplayName("DEF-24: dígito verificador errado, sequência repetida ou texto são recusados")
    void shouldRejectInvalidDocuments(String raw) {
        assertThatThrownBy(() -> TaxId.normalizeAndValidate(raw))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo(TaxId.INVALID_TAX_ID);
    }
}
