-- ============================================================================
-- V3: Create Financial Operations Core Tables
-- Internal virtual ledger accounts, categories, counterparties, entries & attachments
-- ============================================================================

CREATE TABLE financial_accounts (
    id UUID NOT NULL DEFAULT uuidv7(),
    name VARCHAR(120) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    initial_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_financial_accounts PRIMARY KEY (id),
    CONSTRAINT uq_financial_accounts_name UNIQUE (name),
    CONSTRAINT ck_financial_accounts_type CHECK (account_type IN ('OPERATIONAL_LEDGER', 'TREASURY_LEDGER', 'PETTY_CASH'))
);

CREATE TABLE categories (
    id UUID NOT NULL DEFAULT uuidv7(),
    name VARCHAR(100) NOT NULL,
    category_type VARCHAR(20) NOT NULL,
    requires_attachment_always BOOLEAN NOT NULL DEFAULT FALSE,
    is_fixed_cost BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name_type UNIQUE (name, category_type),
    CONSTRAINT ck_categories_type CHECK (category_type IN ('PAYABLE', 'RECEIVABLE', 'BOTH'))
);

CREATE TABLE counterparties (
    id UUID NOT NULL DEFAULT uuidv7(),
    legal_name VARCHAR(180) NOT NULL,
    trade_name VARCHAR(180),
    tax_id VARCHAR(20),
    counterparty_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_counterparties PRIMARY KEY (id),
    CONSTRAINT uq_counterparties_tax_id UNIQUE (tax_id),
    CONSTRAINT ck_counterparties_type CHECK (counterparty_type IN ('CUSTOMER', 'SUPPLIER', 'GOVERNMENT', 'EMPLOYEE', 'OTHER'))
);

CREATE TABLE financial_entries (
    id UUID NOT NULL DEFAULT uuidv7(),
    idempotency_key VARCHAR(100),
    parent_entry_id UUID,
    root_entry_id UUID,
    account_id UUID NOT NULL,
    category_id UUID NOT NULL,
    counterparty_id UUID,
    entry_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    competence_date DATE NOT NULL,
    due_date DATE NOT NULL,
    settlement_date DATE,
    original_amount NUMERIC(19, 2) NOT NULL,
    settled_amount NUMERIC(19, 2),
    discount_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    interest_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    late_fee_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    cancellation_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_financial_entries PRIMARY KEY (id),
    CONSTRAINT uq_financial_entries_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_financial_entries_parent FOREIGN KEY (parent_entry_id) REFERENCES financial_entries (id) ON DELETE RESTRICT,
    CONSTRAINT fk_financial_entries_root FOREIGN KEY (root_entry_id) REFERENCES financial_entries (id) ON DELETE RESTRICT,
    CONSTRAINT fk_financial_entries_account FOREIGN KEY (account_id) REFERENCES financial_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_financial_entries_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_financial_entries_counterparty FOREIGN KEY (counterparty_id) REFERENCES counterparties (id) ON DELETE RESTRICT,
    CONSTRAINT ck_financial_entries_type CHECK (entry_type IN ('PAYABLE', 'RECEIVABLE')),
    CONSTRAINT ck_financial_entries_status CHECK (status IN ('PENDING', 'OVERDUE', 'PAID', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT ck_financial_entries_original_amount CHECK (original_amount > 0),
    CONSTRAINT ck_financial_entries_settled_amount CHECK (settled_amount IS NULL OR settled_amount > 0),
    CONSTRAINT ck_financial_entries_discount_non_negative CHECK (discount_amount >= 0),
    CONSTRAINT ck_financial_entries_interest_non_negative CHECK (interest_amount >= 0),
    CONSTRAINT ck_financial_entries_late_fee_non_negative CHECK (late_fee_amount >= 0)
);

CREATE INDEX idx_financial_entries_status_due_date ON financial_entries (status, due_date);
CREATE INDEX idx_financial_entries_parent_id ON financial_entries (parent_entry_id);
CREATE INDEX idx_financial_entries_root_id ON financial_entries (root_entry_id);
CREATE INDEX idx_financial_entries_category_id ON financial_entries (category_id);
CREATE INDEX idx_financial_entries_account_id ON financial_entries (account_id);
CREATE INDEX idx_financial_entries_counterparty_id ON financial_entries (counterparty_id);
CREATE INDEX idx_financial_entries_settlement_date ON financial_entries (settlement_date);

CREATE TABLE fiscal_attachments (
    id UUID NOT NULL DEFAULT uuidv7(),
    entry_id UUID NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256_checksum VARCHAR(64) NOT NULL,
    uploaded_by UUID,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_fiscal_attachments PRIMARY KEY (id),
    CONSTRAINT uq_fiscal_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_fiscal_attachments_entry FOREIGN KEY (entry_id) REFERENCES financial_entries (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fiscal_attachments_content_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_fiscal_attachments_size CHECK (file_size_bytes > 0 AND file_size_bytes <= 10485760)
);

CREATE INDEX idx_fiscal_attachments_entry_id ON fiscal_attachments (entry_id);
