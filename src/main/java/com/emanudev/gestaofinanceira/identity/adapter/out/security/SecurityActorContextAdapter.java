package com.emanudev.gestaofinanceira.identity.adapter.out.security;

import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class SecurityActorContextAdapter implements ActorContextPort {

    private static final String DEFAULT_IP_ADDRESS = "127.0.0.1";

    @Override
    public ActorContext getCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ActorContext.system();
        }

        String ipAddress = resolveClientIpAddress();
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            UUID userId = parseOrDeterministicUuid(jwt.getSubject());
            String userName = firstNonBlank(
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("email"),
                    jwt.getSubject(),
                    "AUTHENTICATED_USER"
            );
            String role = firstNonBlank(
                    jwt.getClaimAsString("role"),
                    extractRoleFromAuthorities(authentication),
                    "ANALISTA"
            );
            return ActorContext.user(userId, userName, role, ipAddress);
        }

        String principalName = authentication.getName();
        if (principalName == null || principalName.isBlank() || "anonymousUser".equalsIgnoreCase(principalName)) {
            return ActorContext.system();
        }

        UUID userId = parseOrDeterministicUuid(principalName);
        String role = firstNonBlank(extractRoleFromAuthorities(authentication), "ANALISTA");
        return ActorContext.user(userId, principalName, role, ipAddress);
    }

    private String resolveClientIpAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                int commaIdx = forwardedFor.indexOf(',');
                String clientIp = commaIdx > 0 ? forwardedFor.substring(0, commaIdx).trim() : forwardedFor.trim();
                if (!clientIp.isEmpty()) {
                    return clientIp;
                }
            }
            String remoteAddr = request.getRemoteAddr();
            if (remoteAddr != null && !remoteAddr.isBlank()) {
                return remoteAddr;
            }
        }
        return DEFAULT_IP_ADDRESS;
    }

    private String extractRoleFromAuthorities(Authentication authentication) {
        if (authentication.getAuthorities() == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority();
            if (auth != null && !auth.isBlank()) {
                return auth.startsWith("ROLE_") ? auth.substring(5) : auth;
            }
        }
        return null;
    }

    private UUID parseOrDeterministicUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.nameUUIDFromBytes("unknown-user".getBytes(StandardCharsets.UTF_8));
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
