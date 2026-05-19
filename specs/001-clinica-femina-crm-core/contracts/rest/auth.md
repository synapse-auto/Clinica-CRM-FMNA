# Contratos REST — Autenticação

## POST /api/auth/login

Autentica usuário com email + senha. Emite JWT (access token) + refresh token opaco.

**Request**
```json
{
  "email": "renata@clinicafemina.com.br",
  "senha": "********"
}
```

**Response 200**
```json
{
  "accessToken": "eyJ...",
  "expiraEm": "2026-05-19T14:45:00Z",
  "usuario": {
    "id": 1,
    "nome": "Dra. Renata Fiuza",
    "email": "renata@clinicafemina.com.br",
    "perfil": "GESTOR",
    "capacidades": ["VIEW_DASHBOARD", "EXPORT_CONTACTS", "TRANSFER_CONVERSATIONS"],
    "temaPreferencia": "CLARO",
    "perfilMedico": { "especialidade": "OBSTETRICIA_PRE_NATAL", "crm": "44-12345" }
  }
}
```

Cookie setado: `refresh_token=<opaco>; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`.

**Response 401**
```json
{ "error": { "code": "CREDENCIAIS_INVALIDAS", "message": "..." } }
```

**Response 403** (fora do horário do atendente)
```json
{ "error": { "code": "FORA_DO_HORARIO", "message": "Acesso bloqueado fora do horário de trabalho do atendente" } }
```

---

## POST /api/auth/refresh

Troca refresh token (do cookie) por novo access token.

**Request**: body vazio, cookie `refresh_token` obrigatório.

**Response 200**
```json
{ "accessToken": "eyJ...", "expiraEm": "2026-05-19T15:00:00Z" }
```

**Response 401**: refresh expirado/revogado.

---

## POST /api/auth/logout

Revoga refresh token corrente, limpa cookie.

**Response 204**: sem body.

---

## GET /api/auth/me

Retorna usuário autenticado (frontend usa no boot da página para hidratar estado de auth).

**Response 200**: mesma estrutura do campo `usuario` do login.
