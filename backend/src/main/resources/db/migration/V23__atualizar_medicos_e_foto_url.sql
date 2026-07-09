-- 1. Atualizar nomes dos medicos para teste (Abimael e Medico Teste)
UPDATE usuario
SET nome = 'Abimael'
WHERE nome = 'Médico 01' AND perfil = 'MEDICO'
  AND clinica_id = (SELECT id FROM clinica WHERE slug = 'ultramedical');

UPDATE usuario
SET nome = 'Médico Teste'
WHERE nome = 'Médico 02' AND perfil = 'MEDICO'
  AND clinica_id = (SELECT id FROM clinica WHERE slug = 'ultramedical');

-- 2. Adicionar coluna foto_url na tabela paciente (para WhatsApp/outros)
ALTER TABLE paciente ADD COLUMN foto_url VARCHAR(500);
