package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    public UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Optional<User> findByEmail(@NotBlank String email) {
        return repository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findById(@NotNull UUID id) {
        return repository.findById(id)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    public User save(@NotNull User user) {
        UserJpaEntity saved = repository.save(UserJpaEntity.fromDomain(user));
        return saved.toDomain();
    }
}
