-- Atualiza somente o nome exibido da clínica FMNA.
-- O slug fmna permanece estável para não alterar roteamento, banco ou integrações.
UPDATE clinica
SET nome = 'Femina'
WHERE lower(trim(slug)) = 'fmna'
  AND lower(trim(nome)) = 'fmna';
