package com.emanudev.gestaofinanceira.shared.adapter.in.web;

import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientAddress {

    private ClientAddress() {
    }

    /**
     * Uses only the socket address. Behind a reverse proxy, set server.forward-headers-strategy so the
     * container rewrites it from X-Forwarded-For sent by trusted proxies only.
     */
    public static Optional<String> current() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(attributes -> attributes.getRequest().getRemoteAddr())
                .map(String::trim)
                .filter(ip -> !ip.isBlank());
    }
}
