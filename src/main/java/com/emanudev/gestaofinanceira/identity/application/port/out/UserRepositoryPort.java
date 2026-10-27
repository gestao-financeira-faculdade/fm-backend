package com.emanudev.gestaofinanceira.identity.application.port.out;

import com.emanudev.gestaofinanceira.identity.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);

    User save(User user);
}
