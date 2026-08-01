# Plano de Cobertura de Testes do Backend BeeFit

## Objetivo

Implementar uma cobertura satisfatória de testes para o backend, com suíte verde, relatório JaCoCo e proteção dos fluxos de maior risco: autenticação, perfil, treinos, templates, catálogo, mappers, exceções globais e configuração básica da aplicação.

O plano deve ser executado de forma incremental. A primeira meta é pragmática: garantir que `./mvnw clean test` e `./mvnw verify` passem, gerar relatório em `target/site/jacoco` e impedir regressões abaixo do mínimo configurado.

## Ordem Recomendada de Implementação

### 1. Corrigir a suíte quebrada

- Tornar o `DatabaseSeeder` controlável por configuração.
- Desabilitar o seed automático no profile de teste.
- Ajustar `BeefitApiApplicationTests` para carregar a aplicação com `test`.
- Garantir que `./mvnw clean test` não dependa de artefatos antigos em `target`.

Critério de pronto:

- `./mvnw clean test` passa com pelo menos o smoke test da aplicação.
- O profile de teste não tenta subir repositórios JPA quando eles foram excluídos do contexto.

### 2. Adicionar JaCoCo

- Configurar `jacoco-maven-plugin` no `pom.xml`.
- Usar versão compatível com Java 25.
- Ativar os goals:
  - `prepare-agent`;
  - `report`;
  - `check`.
- Gerar relatório HTML em `target/site/jacoco`.
- Começar com limites moderados:
  - line coverage mínimo de `50%`;
  - branch coverage mínimo de `40%`.
- Excluir do check apenas código de baixo valor para cobertura, como DTOs triviais, exceptions simples e a classe principal.

Critério de pronto:

- `./mvnw verify` gera relatório.
- O build falha se a cobertura cair abaixo dos limites.

### 3. Reativar ou recriar `WorkoutSessionServiceTest`

- Substituir o arquivo comentado por testes ativos com JUnit 5 e Mockito.
- Cobrir:
  - início de treino sem template;
  - bloqueio de treino duplicado em progresso;
  - início com template do próprio usuário;
  - rejeição de template de outro usuário;
  - finalização com snapshot válido;
  - rejeição de treino inexistente;
  - rejeição de treino de outro usuário;
  - rejeição de estado inválido;
  - adição de grupo muscular válido;
  - rejeição de grupo muscular inválido.

Critério de pronto:

- O comportamento principal de `WorkoutSessionService` fica protegido sem exigir banco real.

### 4. Cobrir `AuthService`, `UserService` e `WorkoutTemplateService`

- `AuthService`:
  - usuário encontrado por email;
  - usuário inexistente;
  - login com autenticação válida;
  - registro com sucesso;
  - username duplicado;
  - email duplicado.
- `UserService`:
  - leitura de perfil;
  - usuário inexistente;
  - atualização de perfil;
  - troca de senha válida;
  - troca de senha inválida;
  - atualização de `ghostMode`;
  - deleção com senha correta;
  - rejeição de deleção com senha incorreta.
- `WorkoutTemplateService`:
  - criação de template;
  - usuário inexistente;
  - listagem de templates do usuário.

Critério de pronto:

- Services críticos têm testes de sucesso e erro.
- Testes de persistência, migrations e contexto completo usam PostgreSQL 16 via
  Testcontainers e exigem Docker disponível localmente ou no CI.

### 5. Cobrir controllers

- Cobrir controllers por unidade ou `MockMvc`, mantendo services mockados.
- Testar status HTTP, corpo de resposta e delegação correta aos services/repositories.
- Priorizar:
  - `AuthController`;
  - `ProfileController`;
  - `WorkoutController`;
  - `WorkoutTemplateController`;
  - `ExerciseCatalogController`.

Critério de pronto:

- A camada controller tem testes para respostas `200`, `201`, `204` e `404` quando aplicável.
- Payloads básicos e IDs autenticados são repassados corretamente aos services.

### 6. Cobrir mappers e exceptions globais

- Testar `WorkoutMuscleGroupMapper` com valores válidos e inválidos.
- Testar smoke tests dos mappers MapStruct principais.
- Testar `GlobalExceptionHandler` para conflitos, not found, senha inválida, treino inválido e grupo muscular inválido.

Critério de pronto:

- Conversões compartilhadas e payloads de erro têm testes diretos.

### 7. Subir cobertura gradualmente

- Revisar o relatório JaCoCo depois da primeira suíte estável.
- Subir limites em etapas:
  - `60% line / 50% branch`;
  - depois `70% line / 60% branch`, se o custo de manutenção continuar aceitável.
- Priorizar novas coberturas em services e regras de negócio antes de DTOs, entidades simples ou getters/setters.

## Comandos de Verificação

```bash
./mvnw clean test
./mvnw verify
```

## Critério Final de Aceitação

- `./mvnw clean test` passa.
- `./mvnw verify` passa.
- JaCoCo gera relatório em `target/site/jacoco`.
- JaCoCo falha o build abaixo dos limites configurados.
- Tests cobrem services críticos, controllers principais, mappers e exception handler.
- A suíte não depende de banco real ou Docker para rodar localmente.
