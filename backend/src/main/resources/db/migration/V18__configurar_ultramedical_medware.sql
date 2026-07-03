UPDATE clinica
SET external_provider = 'MEDWARE',
    atualizado_em = NOW()
WHERE slug = 'ultramedical'
  AND external_provider IS DISTINCT FROM 'MEDWARE';
