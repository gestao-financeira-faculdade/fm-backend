-- ============================================================================
-- V4: Seed Default Virtual Account, Categories and Sample Counterparties
-- ============================================================================

INSERT INTO financial_accounts (id, name, account_type, initial_balance, currency, is_active)
VALUES (
    '01950000-0000-7000-8000-000000000001',
    'Caixa Consolidado Matriz',
    'OPERATIONAL_LEDGER',
    100000.00,
    'BRL',
    TRUE
) ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (id, name, category_type, requires_attachment_always, is_fixed_cost, is_active)
VALUES
    ('01950000-0000-7000-8000-000000000101', 'Impostos', 'PAYABLE', TRUE, FALSE, TRUE),
    ('01950000-0000-7000-8000-000000000102', 'Fornecedores Operacionais', 'PAYABLE', FALSE, FALSE, TRUE),
    ('01950000-0000-7000-8000-000000000103', 'Folha e Encargos', 'PAYABLE', FALSE, TRUE, TRUE),
    ('01950000-0000-7000-8000-000000000104', 'Infraestrutura e Cloud', 'PAYABLE', FALSE, TRUE, TRUE),
    ('01950000-0000-7000-8000-000000000105', 'Receita de Mensalidades / Contratos', 'RECEIVABLE', FALSE, FALSE, TRUE),
    ('01950000-0000-7000-8000-000000000106', 'Serviços de Consultoria B2B', 'RECEIVABLE', FALSE, FALSE, TRUE)
ON CONFLICT (name, category_type) DO NOTHING;

INSERT INTO counterparties (id, legal_name, trade_name, tax_id, counterparty_type, is_active)
VALUES
    ('01950000-0000-7000-8000-000000000201', 'Secretaria da Receita Federal do Brasil', 'Receita Federal', '00394460005887', 'GOVERNMENT', TRUE),
    ('01950000-0000-7000-8000-000000000202', 'Cloud Computing Services Brasil Ltda', 'CloudCorp', '12345678000190', 'SUPPLIER', TRUE),
    ('01950000-0000-7000-8000-000000000203', 'Cliente Corporativo Enterprise S.A.', 'Enterprise SA', '98765432000110', 'CUSTOMER', TRUE)
ON CONFLICT (tax_id) DO NOTHING;
