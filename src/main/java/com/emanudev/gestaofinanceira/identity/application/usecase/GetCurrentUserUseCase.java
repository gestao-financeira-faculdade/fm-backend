package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.exception.InvalidCredentialsException;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class GetCurrentUserUseCase {

    private final UserRepositoryPort userRepository;
    private final ActorContextPort actorContextPort;

    public GetCurrentUserUseCase(
            UserRepositoryPort userRepository,
            ActorContextPort actorContextPort
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.actorContextPort = Objects.requireNonNull(actorContextPort, "actorContextPort must not be null");
    }

    @Transactional(readOnly = true)
    public UserProfileResult execute() {
        return getCurrentUser();
    }

    @Transactional(readOnly = true)
    public UserProfileResult getCurrentUser() {
        ActorContext actor = actorContextPort.getCurrentActor();
        if (actor == null || actor.userId() == null) {
            throw new InvalidCredentialsException("UNAUTHENTICATED", "Nenhum usuário autenticado no contexto atual.");
        }
        return getById(actor.userId());
    }

    @Transactional(readOnly = true)
    public UserProfileResult getById(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Usuário não encontrado: " + userId));
        return UserProfileResult.from(user);
    }
}
