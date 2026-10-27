package com.emanudev.gestaofinanceira.simulation.domain.exception;

import com.emanudev.gestaofinanceira.shared.domain.exception.DomainException;

public class RiskEngineUnavailableException extends DomainException {

    public static final String ERROR_CODE = "RISK_ENGINE_UNAVAILABLE";

    public RiskEngineUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public RiskEngineUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
