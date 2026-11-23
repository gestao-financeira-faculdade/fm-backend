package com.emanudev.gestaofinanceira.identity.application.port.out;

import java.time.Instant;

public interface AccessTokenBlocklistPort {

    void block(String tokenId, Instant expiresAt);

    boolean isBlocked(String tokenId);
}
