UPDATE counterparties c
SET tax_id = NULLIF(regexp_replace(c.tax_id, '\D', '', 'g'), '')
WHERE c.tax_id IS NOT NULL
  AND c.tax_id IS DISTINCT FROM NULLIF(regexp_replace(c.tax_id, '\D', '', 'g'), '')
  AND NOT EXISTS (
      SELECT 1
      FROM counterparties o
      WHERE o.id <> c.id
        AND o.tax_id IS NOT NULL
        AND regexp_replace(o.tax_id, '\D', '', 'g') = regexp_replace(c.tax_id, '\D', '', 'g')
  );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM financial_accounts GROUP BY lower(name) HAVING count(*) > 1) THEN
        RAISE WARNING 'uq_financial_accounts_name_ci not created: financial_accounts has names that differ only by case';
    ELSE
        CREATE UNIQUE INDEX uq_financial_accounts_name_ci ON financial_accounts (lower(name));
    END IF;

    IF EXISTS (SELECT 1 FROM categories GROUP BY lower(name), category_type HAVING count(*) > 1) THEN
        RAISE WARNING 'uq_categories_name_type_ci not created: categories has names that differ only by case';
    ELSE
        CREATE UNIQUE INDEX uq_categories_name_type_ci ON categories (lower(name), category_type);
    END IF;

    IF EXISTS (
        SELECT 1 FROM counterparties
        WHERE tax_id IS NOT NULL
        GROUP BY regexp_replace(tax_id, '\D', '', 'g')
        HAVING count(*) > 1
    ) THEN
        RAISE WARNING 'counterparties has tax ids that are equal after removing punctuation; review them manually';
    END IF;
END
$$;
