package com.emanudev.gestaofinanceira.identity.application.port.out;

import java.time.Duration;
import java.util.Optional;

public interface LoginAttemptPort {

    Optional<Duration> lockRemaining(String email, String clientIp);

    void recordFailure(String email, String clientIp);

    void reset(String email, String clientIp);
}
