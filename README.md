# 1. BeeFit - API

O **BeeFit** é uma plataforma digital voltada para praticantes de musculação que transforma a rotina de exercícios em uma experiência social e gamificada. O sistema combina o rastreamento de treinos (Track) com mecânicas de colecionismo de cartas virtuais e interações sociais em grupo, como as Colmeias.

Esta é a API do sistema, desenvolvida utilizando o ecossistema **Spring Boot** (Java 25) e integrada nativamente a contêineres **Docker** para simplificar e unificar o ambiente de desenvolvimento entre sistemas Linux e Windows/WSL2.

---

## 2. Como Começar (Passo a Passo)

Graças ao suporte automatizado do Spring Boot com o Docker Compose, você não precisa subir o banco de dados manualmente pelo terminal antes de iniciar a aplicação. Tudo é resolvido de forma integrada na inicialização do app.

### 2.1. Pré-requisitos
Antes de rodar o projeto, certifique-se de ter instalado em sua máquina:
* **Java 25 JDK**
* **Docker** e **Docker Compose**

### 2.2. Executando o Projeto

1. **Clone o repositório** para a sua máquina local:
   ```bash
   git clone [https://github.com/seu-usuario/beefit-api.git](https://github.com/seu-usuario/beefit-api.git)
   cd beefit-api

    Certifique-se de que o Docker Daemon está rodando em seu sistema. No Linux (como Ubuntu), você pode verificar e ativar usando:
    Bash

    # Verificar status do serviço
    sudo systemctl status docker

    # Ligar o serviço (se estiver inativo)
    sudo systemctl start docker

    Inicie a aplicação através de sua IDE de preferência executando a classe principal BeefitApiApplication ou diretamente pelo terminal usando o Maven Wrapper:
    Bash

    ./mvnw spring-boot:run

        💡 O que acontece por baixo dos panos? O Spring Boot vai ler o arquivo docker-compose.yml, fará o download das imagens isoladas do PostgreSQL 16 e do pgAdmin, iniciará os serviços, aguardará o sinal de prontidão (Healthy) e conectará o pool de dados automaticamente na porta configurada de forma transparente.

3. Stack Tecnológica & Dependências

As principais dependências e tecnologias configuradas no arquivo pom.xml deste projeto incluem:

    Spring Web (Web MVC): Camada base para a construção da API RESTful e gerenciamento dos endpoints HTTP e eventos em tempo real (SSE).

    Spring Data JPA & Hibernate: Camada de persistência abstrata responsável pelo mapeamento objeto-relacional das entidades diretamente para o banco de dados.

    PostgreSQL Driver: Driver oficial de conectividade com o banco de dados relacional PostgreSQL adotado pelo MVP.

    Spring Boot Docker Compose Support: Módulo de infraestrutura que gerencia o ciclo de vida dos contêineres de desenvolvimento (start-and-stop) junto com a inicialização do app.

    Spring Security & Validation: Mecanismos de segurança para blindagem de endpoints através de JWT e validação de restrições de payload recebidos nas requisições HTTP (como os campos obrigatórios do cadastro de usuário).

    Lombok: Biblioteca voltada para a produtividade da equipe, removendo códigos repetitivos (boilerplate) através de anotações automáticas de getters, setters e construtores.

4. Estrutura de Pastas

O código-fonte segue as boas práticas de separação de responsabilidades (atendendo ao requisito RNF-009), dividindo-se nos seguintes subpacotes em com.beefit.api:
Plaintext

src/main/java/com/beefit/api/
│
├── configuration/   # Classes de configuração global (CORS, Security, filtros JWT)
├── controller/      # Controladores REST que expõem os endpoints da API (ex: WorkoutController)
├── domain/          # Entidades de domínio JPA mapeadas para tabelas (ex: User, Card, Set)
├── repository/      # Interfaces Spring Data JPA para comunicação com o banco de dados
├── service/         # Camada com as regras de negócio e fórmulas matemáticas do sistema (ex: CardService)
│
└── BeefitApiApplication.java  # Classe de inicialização principal do Spring Boot

5. Configurações Importantes & Segurança
5.1. Variáveis de Ambiente e Proteção de Dados

O arquivo application.yaml foi arquitetado para nunca conter chaves ou senhas expostas diretamente em texto puro.

    Segurança do Banco: As credenciais e o mapeamento de volumes físicos de dados (postgres_data) estão isolados e configurados para evitar conflitos de permissões locais em distribuições Linux, utilizando a propriedade PGDATA apontada para uma subpasta dedicada.

5.2. Integration com o pgAdmin (Interface Gráfica)

O ambiente do Docker Compose expõe uma interface gráfica web para o monitoramento rápido do banco de dados em desenvolvimento:

    Endereço Local: http://localhost:8081

    E-mail de acesso padrão: admin@beefit.com

    Senha padrão: admin_password

    Host para conexão interna no Docker: postgres (Porta 5432 padrão interna).
