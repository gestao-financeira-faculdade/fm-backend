package com.emanudev.gestaofinanceira.identity.adapter.out.security;

import com.emanudev.gestaofinanceira.shared.adapter.in.web.ClientAddress;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

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
                    extractRoleFromAuthorities(authentication),
                    jwt.getClaimAsString("role"),
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
        return ClientAddress.current().orElse(DEFAULT_IP_ADDRESS);
    }

    private String extractRoleFromAuthorities(Authentication authentication) {
        return Optional.ofNullable(authentication.getAuthorities())
                .stream()
                .flatMap(authorities -> authorities.stream().map(GrantedAuthority::getAuthority))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(auth -> !auth.isEmpty())
                .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                .findFirst()
                .orElse(null);
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
        return Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .filter(candidate -> !candidate.isBlank())
                .findFirst()
                .orElse(null);
    }
}
