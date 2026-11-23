package com.emanudev.gestaofinanceira.identity.adapter.in.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldTrimEmailBeforeValidation() {
        LoginRequest request = new LoginRequest("  admin@gestaofinanceira.com ", "Admin@123");

        assertThat(request.email()).isEqualTo("admin@gestaofinanceira.com");
        assertThat(validator.validate(request)).isEmpty();
    }
}
