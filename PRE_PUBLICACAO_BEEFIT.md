# BeeFit — auditoria atualizada para publicação

> Registro histórico de auditoria. As contagens e versões abaixo representam o
> momento desta revisão; o estado atual deve ser confirmado pelos READMEs e
> gates automatizados dos dois repositórios.

Atualizado após a migração completa dos testes para PostgreSQL.

## Estado atual

- Frontend: build de produção concluído, 44 testes passando e lint sem erros bloqueantes; permanecem 7 warnings.
- Backend: 94 testes passando com PostgreSQL 16 real via Testcontainers.
- Flyway: migrations V1 a V5 aplicadas com sucesso em PostgreSQL.
- Banco de testes: H2 removido completamente; não há dependência nem configuração H2 no projeto.
- Empacotamento backend e gates JaCoCo passam com `./mvnw verify`.
- O build frontend ainda gera um chunk principal de aproximadamente 1,36 MB.

## Concluído

### Persistência e testes

- Remoção da dependência H2.
- Testes de contexto, repository e migrations executados contra PostgreSQL 16.
- Perfil de testes reativado com JPA, datasource, Flyway e repositórios reais.
- Configuração de containers PostgreSQL centralizada em `backend/src/test/java/com/beefit/api/support/`.

### Frontend e fluxo de treino

- Bypass de autenticação limitado ao modo de desenvolvimento.
- Timeout e renovação de sessão implementados no cliente.
- Chave de idempotência enviada ao finalizar treinos.
- Persistência e recuperação da chave de finalização implementadas.
- Validação local de formato, tamanho e dimensões de imagens.
- Limpeza de URLs temporárias e recuperação de treino pendente implementadas.
- Telas inativas desmontadas para evitar timers e chamadas desnecessárias.
- Melhorias de acessibilidade em senha, campos e idioma do documento.
- Backup Android desabilitado, FileProvider restrito e minificação habilitada.

### Segurança e privacidade da API

- `GET /api/media/{id}` exige autenticação e aplica ownership, vínculo com perfil/post, amizade aceita, privacidade, modo fantasma e remoção do post.
- Uploads ainda não associados são visíveis somente ao dono; fotos de perfil são visíveis a usuários autenticados; administradores acessam apenas mídias vinculadas a posts para moderação.
- Operações de consumo de mídia validam o dono, inclusive ao trocar a foto de perfil; posts, curtidas e importação de template respeitam a mesma visibilidade social.
- Todos os endpoints `/api/admin/**` possuem testes HTTP contra usuários comuns, tokens inválidos e administradores banidos.
- CORS de produção aceita somente origens exatas; produção rejeita HTTP e respeita headers encaminhados pelo proxy.
- Builds exigem `VITE_API_URL`, bloqueiam API HTTP fora de loopback explicitamente autorizado e recusam `VITE_DEV_AUTH_BYPASS=true`.
- O cliente carrega mídias protegidas com Bearer token e revoga URLs temporárias; builds nativos continuam obrigados a usar Secure Storage, sem fallback web no runtime Capacitor.
- Rate limiting compartilhado no PostgreSQL protege login, cadastro, refresh, troca de senha e upload; as identidades dos buckets são armazenadas somente como SHA-256.
- Headers de segurança foram configurados e logs diretos/argumentos de auditoria que poderiam expor dados foram removidos.
- A finalização de treino possui chave única por usuário e uma única sessão ativa por usuário garantidas no PostgreSQL; reenvios concorrentes e após timeout não duplicam post, carta, inventário ou contadores.
- O cliente persiste o payload exato, inclusive foto já enviada, e recupera automaticamente uma finalização pendente após reinício, retorno da rede ou nova sessão autenticada.

### Qualidade local e segurança do repositório

- `npm ci`, `npm audit` e `npm audit --omit=dev` concluídos; o lockfile não possui vulnerabilidades conhecidas pelo npm no momento da verificação.
- Vite atualizado para 6.4.3 para corrigir vulnerabilidades da cadeia de desenvolvimento.
- Históricos do frontend e backend examinados com Gitleaks, sem segredos confirmados.
- O `.env.local` encontrado no histórico antigo continha somente configuração local sem credenciais; foi decidido não reescrever o histórico.
- A auditoria OSV do backend identificou atualizações de dependências que ainda precisam de decisão e implementação.

## Decisões e pendências restantes

As pendências de acesso ao beta, dependências, CI/deploy, configuração de produção, uploads, privacidade, moderação, resiliência e validação nativa foram transferidas para [`OBSERVATIONS.md`](./OBSERVATIONS.md). Esse documento descreve as alternativas, os efeitos práticos no desenvolvimento e no produto e os riscos de manter cada ponto como está.
