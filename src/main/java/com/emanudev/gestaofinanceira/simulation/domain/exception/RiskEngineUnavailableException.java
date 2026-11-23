package com.emanudev.gestaofinanceira.simulation.domain.exception;

import com.emanudev.gestaofinanceira.shared.domain.exception.ServiceUnavailableException;

public class RiskEngineUnavailableException extends ServiceUnavailableException {

    public static final String ERROR_CODE = "RISK_ENGINE_UNAVAILABLE";

    public RiskEngineUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public RiskEngineUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
