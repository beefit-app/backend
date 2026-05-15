# BeeFit

> Rede social de academia

---

## Índice

- [Padrões de Commit](#-padrões-de-commit)
- [Documentação](#-documentação)
- [Como Executar e Configurações](#-execução)
- [Licença](#-licença)

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

# Como Começar (Passo a Passo)

Graças ao suporte automatizado do Spring Boot com o Docker Compose, você não precisa subir o banco de dados manualmente pelo terminal antes de iniciar a aplicação. Tudo é resolvido de forma integrada na inicialização do app.

## Pré-requisitos

Antes de rodar o projeto, certifique-se de ter instalado em sua máquina:

- **Java 25 JDK**
- **Docker**
- **Docker Compose**

## Executando o Projeto

### 1. Clone o repositório

```bash
git clone https://github.com/seu-usuario/beefit-api.git
cd beefit-api
```

### 2. Certifique-se de que o Docker está rodando

No Linux (como Ubuntu), você pode verificar e ativar usando:

### Verificar status do serviço
```bash
sudo systemctl status docker
```

### Ligar o serviço (se estiver inativo)
```bash
sudo systemctl start docker
```

### 3. Execute a aplicação

Você pode iniciar a aplicação através da sua IDE executando a classe principal BeefitApiApplication ou diretamente pelo terminal utilizando o Maven Wrapper:

```bash
./mvnw spring-boot:run
```

💡 O que acontece por baixo dos panos?
O Spring Boot irá ler automaticamente o arquivo docker-compose.yml, baixar as imagens isoladas do PostgreSQL 16 e do pgAdmin, iniciar os serviços, aguardar o sinal de prontidão (healthy) e conectar o pool de dados automaticamente na porta configurada de forma transparente.

# Stack Tecnológica & Dependências

As principais dependências e tecnologias configuradas no arquivo pom.xml incluem:

```bash
Spring Web (Web MVC)
Camada base para construção da API RESTful e gerenciamento dos endpoints HTTP e eventos em tempo real (SSE).
Spring Data JPA & Hibernate
Camada de persistência responsável pelo mapeamento objeto-relacional das entidades diretamente para o banco de dados.
PostgreSQL Driver
Driver oficial de conectividade com o banco de dados PostgreSQL utilizado no MVP.
Spring Boot Docker Compose Support
Módulo responsável pelo gerenciamento automático do ciclo de vida dos contêineres de desenvolvimento.
Spring Security & Validation
Recursos de segurança para proteção de endpoints via JWT e validação de payloads recebidos nas requisições HTTP.
Lombok
Biblioteca voltada à produtividade, reduzindo código repetitivo (boilerplate) através de anotações automáticas.
Estrutura de Pastas
```

O código-fonte segue boas práticas de separação de responsabilidades (atendendo ao requisito RNF-009), organizando-se da seguinte forma:

src/main/java/com/beefit/api/
│
├── configuration/   # Configurações globais (CORS, Security, filtros JWT)
├── controller/      # Controladores REST e endpoints da API
├── domain/          # Entidades JPA mapeadas para tabelas
├── repository/      # Interfaces Spring Data JPA
├── service/         # Regras de negócio e serviços do sistema
│
└── BeefitApiApplication.java  # Classe principal do Spring Boot

# Configurações Importantes & Segurança

## Variáveis de Ambiente e Proteção de Dados

O arquivo application.yaml foi estruturado para evitar exposição de credenciais diretamente no código-fonte.

## JWT Secret

O segredo de assinatura dos tokens JWT utiliza injeção segura de propriedades:

```bash
${JWT_SECRET:valor_padrao}
```

Substitua o valor padrão por uma variável de ambiente real em ambientes além do desenvolvimento local.

## Segurança do Banco

As credenciais e volumes persistentes (postgres_data) estão isolados para evitar conflitos de permissões em ambientes Linux, utilizando a propriedade PGDATA apontando para uma subpasta dedicada.

## Integração com o pgAdmin

O ambiente Docker Compose disponibiliza uma interface gráfica web para administração rápida do banco de dados.

### Acesso Local
URL: http://localhost:8081
E-mail padrão: admin@beefit.com
Senha padrão: admin_password
Host interno Docker: postgres
Porta interna: 5432

---

## Licença

Distribuído sob a licença MIT. Veja [`LICENSE`](LICENSE) para mais informações.

---
