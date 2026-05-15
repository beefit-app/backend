# BeeFit

> Rede social de academia

---

## Índice

- [Padrões de Commit](#-padrões-de-commit)
- [Documentação](#-documentação)
- [Licença](#-licença)
- [Como Executar e Configurações](#-execução)

---

## Padrões de Commit

> **Por que isso importa?** Um histórico de commits bem escrito é uma forma de documentação. Ele permite entender _o que_ mudou, _por que_ mudou e _quando_ — sem precisar ler cada linha de código alterada.

Este projeto adota o padrão **[Conventional Commits](https://www.conventionalcommits.org/pt-br/)**.

### Formato

```
<tipo>(escopo opcional): <descrição curta>

[corpo opcional]

[rodapé opcional]
```

### Tipos de Commit

| Tipo       | Quando usar             | Exemplo                                              |
| ---------- | ----------------------- | ---------------------------------------------------- |
| `feat`     | Nova funcionalidade     | `feat(auth): adiciona login com Google`              |
| `fix`      | Correção de bug         | `fix(api): corrige erro 500 no endpoint de usuários` |
| `docs`     | Apenas documentação     | `docs: atualiza instruções de instalação`            |
| `style`    | Formatação, sem lógica  | `style: corrige indentação nos componentes`          |
| `refactor` | Refatoração de código   | `refactor(db): simplifica query de produtos`         |
| `test`     | Testes                  | `test(auth): adiciona testes unitários para JWT`     |
| `chore`    | Tarefas de manutenção   | `chore: atualiza dependências do projeto`            |
| `perf`     | Melhoria de performance | `perf(images): implementa lazy loading`              |
| `ci`       | Integração contínua     | `ci: adiciona pipeline de deploy automático`         |
| `build`    | Sistema de build        | `build: configura webpack para produção`             |
| `revert`   | Reverte um commit       | `revert: reverte feat(auth): login com Google`       |

### Regras

- ✅ Use o **imperativo** na descrição: _"adiciona"_, _"corrige"_, _"remove"_ — não _"adicionado"_ ou _"adicionando"_
- ✅ Limite a linha de descrição a **72 caracteres**
- ✅ Use letras **minúsculas**
- ✅ Seja **específico**: prefira `fix(cart): corrige cálculo de frete para CEPs do RJ` a `fix: bug no carrinho`
- ❌ Evite mensagens vagas como `fix: ajustes`, `update`, `wip`, `changes`

### Exemplos

```bash
# ✅ Bom
feat(checkout): integra gateway de pagamento Stripe
fix(user): corrige validação de CPF com caracteres especiais
docs(api): documenta endpoints de autenticação com exemplos
refactor(hooks): extrai lógica de formulário para useForm

# ❌ Ruim
git commit -m "fix"
git commit -m "ajustes gerais"
git commit -m "terminei a feature"
git commit -m "update 2"
```

### Breaking Changes

Quando uma mudança quebra compatibilidade, adicione `!` após o tipo ou use `BREAKING CHANGE` no rodapé:

```bash
feat(api)!: remove suporte ao endpoint /v1/users

BREAKING CHANGE: o endpoint /v1/users foi removido.
Use /v2/users com autenticação via Bearer token.
```

---

## Documentação

> **Por que documentar?** Código sem documentação é um ativo que se deprecia rapidamente. O programador que escreveu uma função hoje não será o mesmo que precisará mantê-la em seis meses — e muitas vezes nem vai se lembrar da decisão tomada.

### O que documentar

| O quê                   | Onde                         | Por quê                                |
| ----------------------- | ---------------------------- | -------------------------------------- |
| Decisões de arquitetura | `docs/architecture/`         | Evita repetir discussões já resolvidas |
| Endpoints da API        | `docs/api/` ou Swagger       | Facilita integrações                   |
| Variáveis de ambiente   | `.env.example` + comentários | Evita erros silenciosos em produção    |
| Fluxos complexos        | Comentários no código        | Economiza horas de leitura             |
| Decisões não-óbvias     | Comentários `// Por que:`    | Preserva o contexto                    |

### Boas Práticas

```js
// ❌ Comentário inútil — descreve o óbvio
// Incrementa o contador
i++;

// ✅ Comentário útil — explica o porquê
// O índice começa em 1 porque o item 0 é reservado para o placeholder da UI
let i = 1;

// ✅ Explica uma decisão não-óbvia
// Usamos debounce de 400ms em vez de 300ms após testes com usuários
// mostrarem que 300ms causava chamadas duplas em conexões lentas
const handleSearch = debounce(search, 400);
```

---
## Execução

## 2. Como Começar (Passo a Passo)

Graças ao suporte automatizado do Spring Boot com o Docker Compose, você não precisa subir o banco de dados manualmente pelo terminal antes de iniciar a aplicação. Tudo é resolvido de forma integrada na inicialização do app.

### 2.1. Pré-requisitos
Antes de rodar o projeto, certifique-se de ter instalado em sua máquina:
* [cite_start]**Java 25 JDK** [cite: 26]
* [cite_start]**Docker** e **Docker Compose** [cite: 21, 202]

### 2.2. Executando o Projeto

1. **Clone o repositório** para a sua máquina local:
   ```bash
   git clone [https://github.com/seu-usuario/beefit-api.git](https://github.com/seu-usuario/beefit-api.git)
   cd beefit-api
Certifique-se de que o Docker Daemon está rodando em seu sistema. No Linux (como Ubuntu), você pode verificar e ativar usando:Bash# Verificar status do serviço
sudo systemctl status docker

# Ligar o serviço (se estiver inativo)
sudo systemctl start docker
Inicie a aplicação através de sua IDE de preferência executando a classe principal BeefitApiApplication ou diretamente pelo terminal usando o Maven Wrapper:Bash./mvnw spring-boot:run
💡 O que acontece por baixo dos panos? O Spring Boot vai ler o arquivo docker-compose.yml, fará o download das imagens isoladas do PostgreSQL 16 e do pgAdmin, iniciará os serviços, aguardará o sinal de prontidão (Healthy) e conectará o pool de dados automaticamente na porta configurada de forma transparente.3. Stack Tecnológica & DependênciasAs principais dependências e tecnologias configuradas no arquivo pom.xml deste projeto incluem:Spring Web (Web MVC): Camada base para a construção da API RESTful e gerenciamento dos endpoints HTTP e eventos em tempo real (SSE).  Spring Data JPA & Hibernate: Camada de persistência abstrata responsável pelo mapeamento objeto-relacional das entidades diretamente para o banco de dados.  PostgreSQL Driver: Driver oficial de conectividade com o banco de dados relacional PostgreSQL adotado pelo MVP.  Spring Boot Docker Compose Support: Módulo de infraestrutura que gerencia o ciclo de vida dos contêineres de desenvolvimento (start-and-stop) junto com a inicialização do app.  Spring Security & Validation: Mecanismos de segurança para blindagem de endpoints através de JWT e validação de restrições de payload recebidos nas requisições HTTP (como os campos obrigatórios do cadastro de usuário).  Lombok: Biblioteca voltada para a produtividade da equipe, removendo códigos repetitivos (boilerplate) através de anotações automáticas de getters, setters e construtores.4. Estrutura de PastasO código-fonte segue as boas práticas de separação de responsabilidades (atendendo ao requisito RNF-009 ), dividindo-se nos seguintes subpacotes em com.beefit.api:  Plaintextsrc/main/java/com/beefit/api/
│
├── configuration/   # Classes de configuração global (CORS, Security, filtros JWT)
├── controller/      # Controladores REST que expõem os endpoints da API (ex: WorkoutController)
├── domain/          # Entidades de domínio JPA mapeadas para tabelas (ex: User, Card, Set)
├── repository/      # Interfaces Spring Data JPA para comunicação com o banco de dados
├── service/         # Camada com as regras de negócio e fórmulas matemáticas do sistema (ex: CardService)
│
└── BeefitApiApplication.java  # Classe de inicialização principal do Spring Boot
5. Configurações Importantes & Segurança5.1. Variáveis de Ambiente e Proteção de DadosO arquivo application.yaml foi arquitetado para nunca conter chaves ou senhas expostas diretamente em texto puro.  JWT Secret: O segredo de assinatura dos tokens JWT utiliza uma estratégia de injeção segura de propriedades (${JWT_SECRET:...}). Substitua o valor padrão por uma variável de ambiente real no servidor em ambientes além do desenvolvimento local.  Segurança do Banco: As credenciais e o mapeamento de volumes físicos de dados (postgres_data) estão isolados e configurados para evitar conflitos de permissões locais em distribuições Linux, utilizando a propriedade PGDATA apontada para uma subpasta dedicada.5.2. Integração com o pgAdmin (Interface Gráfica)O ambiente do Docker Compose expõe uma interface gráfica web para o monitoramento rápido do banco de dados em desenvolvimento:Endereço Local: http://localhost:8081E-mail de acesso padrão: admin@beefit.comSenha padrão: admin_passwordHost para conexão interna no Docker: postgres (Porta 5432 padrão interna).

---

## Licença

Distribuído sob a licença MIT. Veja [`LICENSE`](LICENSE) para mais informações.

---
