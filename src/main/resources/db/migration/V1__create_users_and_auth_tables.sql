CREATE TABLE users (
    id UUID NOT NULL DEFAULT uuidv7(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'ANALISTA', 'AUDITOR'))
);

CREATE TABLE refresh_tokens (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Initial demonstration users (default password: Admin@123)
INSERT INTO users (id, full_name, email, password_hash, role, is_active, created_at, updated_at)
VALUES
    ('0195f000-0000-7000-8000-000000000001', 'Administrador Financeiro', 'admin@gestaofinanceira.com', '$2a$10$jJ64390i8LKB.lpeQDD5/.kmA.J6bCkm2Tu0Vzp9MAd9DYfq7uVPe', 'ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('0195f000-0000-7000-8000-000000000002', 'Analista Financeiro', 'analista@gestaofinanceira.com', '$2a$10$jJ64390i8LKB.lpeQDD5/.kmA.J6bCkm2Tu0Vzp9MAd9DYfq7uVPe', 'ANALISTA', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('0195f000-0000-7000-8000-000000000003', 'Auditor de Compliance', 'auditor@gestaofinanceira.com', '$2a$10$jJ64390i8LKB.lpeQDD5/.kmA.J6bCkm2Tu0Vzp9MAd9DYfq7uVPe', 'AUDITOR', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
