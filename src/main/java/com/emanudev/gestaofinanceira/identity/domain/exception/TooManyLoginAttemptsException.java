package com.emanudev.gestaofinanceira.identity.domain.exception;

import com.emanudev.gestaofinanceira.shared.domain.exception.RateLimitExceededException;
import java.time.Duration;

public class TooManyLoginAttemptsException extends RateLimitExceededException {

    public static final String ERROR_CODE = "TOO_MANY_LOGIN_ATTEMPTS";

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super(ERROR_CODE, "Muitas tentativas de login sem sucesso. Tente novamente mais tarde.", retryAfter);
    }
}
