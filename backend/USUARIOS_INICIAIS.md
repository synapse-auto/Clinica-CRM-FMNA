# Usuários iniciais

O CRM não possui cadastro público. Os usuários iniciais são criados pelo backend
somente quando as seguintes variáveis estão configuradas no ambiente do serviço:

```env
INITIAL_USERS_ENABLED=true
INITIAL_USERS_JSON=[{"nome":"<nome>","email":"<email>","perfil":"RECEPCIONISTA","password":"<senha-inicial-forte>","adminInterno":false}]
```

O JSON aceita os perfis `GESTOR`, `RECEPCIONISTA` e `MEDICO`. Administradores
internos devem usar o perfil `GESTOR` com `"adminInterno":true`. Eles mantêm
acesso total, mas ficam excluídos das consultas de usuários visíveis da clínica.

Todos os usuários novos recebem:

- senha armazenada exclusivamente como hash BCrypt;
- `must_change_password=true`;
- bloqueio das APIs operacionais até a troca da senha;
- redirecionamento para `/alterar-senha` no primeiro login.

O seed é idempotente: se o e-mail já existir, a senha não é alterada. Para uma
redefinição administrativa explícita, inclua `"resetPassword":true` na entrada
correspondente. Após uma criação ou redefinição:

1. remova o JSON do ambiente;
2. configure `INITIAL_USERS_ENABLED=false`;
3. faça um novo deploy/restart do backend.

Nunca salve o JSON real em arquivos do projeto, logs, tickets ou commits.
