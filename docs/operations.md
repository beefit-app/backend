# Operação do BeeFit

Este runbook cobre o monólito atual e PostgreSQL. O Compose de aplicação serve
para validação local reproduzível; ele não define uma topologia de produção.

## Ambiente local completo

```bash
export JWT_SECRET='substitua-por-um-segredo-aleatorio-com-alta-entropia'
export POSTGRES_PASSWORD='substitua-a-senha-local'
docker compose -f compose.application.yaml up --build --wait
```

- frontend: `http://localhost:4173`
- API: `http://localhost:8080`
- readiness: `http://localhost:8080/actuator/health/readiness`
- liveness: `http://localhost:8080/actuator/health/liveness`

Somente health é público. `metrics` e `prometheus` continuam atrás da
autenticação da API; uma implantação deve preferir rede de gerenciamento
privada e autenticação própria do coletor.

## Banco anterior ao Flyway

O padrão seguro é `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`: a aplicação deve
parar quando encontra tabelas sem `flyway_schema_history`. Não apague o volume e
não habilite baseline às cegas.

1. Pare escritas na aplicação e crie um backup lógico.
2. Confirme que `flyway_schema_history` realmente não existe e liste todas as
   tabelas do schema `public`.
3. Compare o schema e constraints com `V1__initial_schema.sql`. O baseline em
   versão 1 só é válido quando o banco representa exatamente V1.
4. Restaure o backup em um PostgreSQL descartável e execute uma única vez com
   `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`; valide todas as migrations, testes
   funcionais e contagens antes de repetir no banco original.
5. Se o schema não for V1 exato, escreva uma migration de reconciliação revisada
   e teste-a na cópia. Não use `repair` para esconder divergências de checksum.

Exemplo de backup do Compose de desenvolvimento, sem alterar o volume:

```bash
docker compose exec -T postgres pg_dump \
  --username=beefit_user --format=custom --dbname=beefit_db \
  > beefit-$(date +%Y%m%d-%H%M%S).dump
```

Valide o arquivo com `pg_restore --list` e mantenha-o fora do repositório.

## Backup e restauração

- Automatize backups completos e teste restauração periodicamente em banco
  isolado. Backup sem teste de restore não é evidência de recuperabilidade.
- Proteja e criptografe dumps, pois `workout_media` contém imagens e o banco
  contém dados pessoais.
- Para restaurar: suspenda escrita, crie banco vazio compatível, execute
  `pg_restore --exit-on-error`, inicie a mesma versão da aplicação e confira
  readiness, migrations, autenticação e amostras funcionais antes do tráfego.
- Defina RPO/RTO e retenção com produto/operação; o repositório não determina
  esses valores.

## Deploy e migrations

1. Execute `./mvnw verify`, gates frontend e Playwright em banco E2E isolado.
2. Gere e examine SBOMs e scans das imagens.
3. Faça backup e registre versão da aplicação e última migration aplicada.
4. Aplique migrations com uma única instância iniciando; não execute duas
   versões incompatíveis com o mesmo schema simultaneamente.
5. Só libere tráfego quando readiness estiver `UP`; monitore erros por
   `X-Request-ID`, latência, saturação do pool JDBC e espaço do PostgreSQL.

Migrations Flyway são forward-only. Rollback normal reimplanta a versão anterior
somente quando ela permanece compatível com o schema novo. Mudança destrutiva
exige expansão/contração em releases separados. Restaurar backup é último
recurso e perde escritas posteriores ao ponto restaurado.

O job E2E vive no CI do frontend e faz checkout de `beefit-app/backend`. Se o
backend for privado, configure `CROSS_REPO_TOKEN` com acesso somente de leitura;
o token padrão do workflow normalmente não acessa outro repositório privado.

## Incidentes

Registre `X-Request-ID`, horário UTC, endpoint, versão implantada e status HTTP.
Nunca copie tokens, senhas, headers de autenticação ou conteúdo de imagens para
logs/tickets. Health sem detalhes evita expor dependências ao público.
