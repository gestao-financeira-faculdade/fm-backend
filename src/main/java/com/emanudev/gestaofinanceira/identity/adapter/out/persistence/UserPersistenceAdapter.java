package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    public UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return repository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public User save(User user) {
        Objects.requireNonNull(user, "user must not be null");
        UserJpaEntity saved = repository.save(UserJpaEntity.fromDomain(user));
        return saved.toDomain();
    }
}
