-- V13: Garante que clínicas existentes usem o provider correto.
-- UltraMedical (Medware) deve ter external_provider = 'MEDWARE'.
-- FMNA (Darwin) permanece com 'DARWIN'.
-- Este script NÃO altera registros globalmente — apenas corrige o default
-- definido em código, que estava como DARWIN mesmo para clínicas Medware.
-- Ajuste manual por slug se necessário: UPDATE clinica SET external_provider = 'MEDWARE' WHERE slug = 'ultramedical';
-- Nenhuma alteração automática de dados de produção nesta migration.
SELECT 1; -- migration de documentação; ajuste manual no EasyPanel via variável EXTERNAL_PROVIDER
