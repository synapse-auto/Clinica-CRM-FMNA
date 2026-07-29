# Checklist de endpoints N8N

Use esta lista em toda criação ou alteração de `/api/n8n/**`.

- [ ] Método e rota estão liberados corretamente no `SecurityConfig`.
- [ ] `X-N8N-SECRET` é validado.
- [ ] `Content-Type` está documentado.
- [ ] O DTO foi testado com o payload real do workflow.
- [ ] Números aceitam número e string quando apropriado.
- [ ] Aliases camelCase e snake_case necessários estão definidos.
- [ ] Lista serializada como texto foi testada quando o N8N puder produzi-la.
- [ ] Headers obrigatórios possuem testes.
- [ ] JSON inválido retorna um `code` específico.
- [ ] Erros de validação retornam `details` por campo.
- [ ] A semântica de idempotência está definida.
- [ ] Retry idêntico foi testado.
- [ ] Reutilização conflitante retorna `409`, não `400`.
- [ ] Novas constraints e enums estão cobertos por migration e teste.
- [ ] Isolamento por clínica foi testado.
- [ ] Logs não contêm secrets, payloads ou dados sensíveis.
- [ ] Exemplos de cURL e Postman foram atualizados.
- [ ] O teste do controller usa o mesmo JSON do workflow.
- [ ] Push não é tratado como deploy.
- [ ] O workflow real será testado após o deploy.
