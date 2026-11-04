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

-- Initial demonstration users (default password: Admin@123)
INSERT INTO users (id, full_name, email, password_hash, role, is_active, created_at, updated_at)
VALUES
    ('0195f000-0000-7000-8000-000000000001', 'Administrador Financeiro', 'admin@gestaofinanceira.com', '$2a$10$jJ64390i8LKB.lpeQDD5/.kmA.J6bCkm2Tu0Vzp9MAd9DYfq7uVPe', 'ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('0195f000-0000-7000-8000-000000000002', 'Analista Financeiro', 'analista@gestaofinanceira.com', '$2a$10$jJ64390i8LKB.lpeQDD5/.kmA.J6bCkm2Tu0Vzp9MAd9DYfq7uVPe', 'ANALISTA', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('0195f000-0000-7000-8000-000000000003', 'Auditor de Compliance', 'auditor@gestaofinanceira.com', '$2a$10$jJ64390i8LKB.lpeQDD5/.kmA.J6bCkm2Tu0Vzp9MAd9DYfq7uVPe', 'AUDITOR', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
