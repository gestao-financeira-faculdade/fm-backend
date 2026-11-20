package com.emanudev.gestaofinanceira.identity.adapter.out.security;

import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class ActiveUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepositoryPort userRepository;

    public ActiveUserJwtAuthenticationConverter(UserRepositoryPort userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parseUserId(jwt.getSubject())
                .flatMap(userRepository::findById)
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("Token subject is not an active user"));

        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())),
                jwt.getSubject()
        );
    }

    private static Optional<UUID> parseUserId(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
