-- Corrige apenas aliases genéricos legados da UltraMedical.
-- A associação continua baseada em dados da clínica e no perfil MEDICO.
UPDATE usuario
SET nome = 'Abimael'
WHERE clinica_id = (SELECT id FROM clinica WHERE slug = 'ultramedical')
  AND perfil = 'MEDICO'
  AND nome LIKE 'M%dico 01';

UPDATE usuario
SET nome = 'Médico Teste'
WHERE clinica_id = (SELECT id FROM clinica WHERE slug = 'ultramedical')
  AND perfil = 'MEDICO'
  AND nome LIKE 'M%dico 02';
