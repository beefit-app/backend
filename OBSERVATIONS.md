# BeeFit — observações e decisões antes do beta

> Registro histórico de auditoria. As contagens de testes, versões e pendências
> refletem a data em que cada seção foi escrita e podem ter sido superadas por
> commits posteriores. Para o estado executável atual, consulte os READMEs e os
> gates de cada repositório.

Este documento concentra as pendências que ainda precisam ser avaliadas antes
da publicação do BeeFit. Ele não representa uma ordem automática de execução:
os pontos que alteram acesso, infraestrutura, dependências ou comportamento do
produto exigem uma decisão explícita antes da implementação.

O [`PRE_PUBLICACAO_BEEFIT.md`](./PRE_PUBLICACAO_BEEFIT.md) permanece como
registro das correções e validações já concluídas.

## Resumo executivo

O frontend e o backend estão compilando, os testes automatizados passam e os
gates locais de cobertura foram atendidos. Isso indica que existe uma base
funcional para um beta, mas não significa que a aplicação esteja pronta para
ser exposta publicamente sem controles adicionais.

As decisões mais importantes antes do beta são:

1. definir como o acesso será realmente restrito aos testadores;
2. atualizar as dependências vulneráveis do backend;
3. definir a infraestrutura de homologação e produção;
4. decidir posteriormente a estratégia de CI e deploy;
5. concluir os testes nativos, operacionais, de privacidade e de resiliência.

## Validações locais já realizadas

- O backend passou em `./mvnw verify`, com 94 testes, PostgreSQL 16 real via
  Testcontainers e os limites de cobertura do JaCoCo.
- O frontend passou em 44 testes e no build de produção. O lint não tem erros
  bloqueantes, mas mantém 7 warnings.
- `npm ci` foi executado com sucesso a partir do lockfile.
- `npm audit` e `npm audit --omit=dev` terminaram sem vulnerabilidades depois
  da atualização do Vite para 6.4.3 e da correção do lockfile.
- O histórico dos dois repositórios foi examinado com Gitleaks. Não foram
  encontrados segredos; falsos positivos em bundles minificados antigos do
  frontend foram tratados por uma regra limitada em `frontend/.gitleaks.toml`.
- Um `.env.local` já foi versionado no histórico do frontend, mas continha
  somente uma URL de loopback e uma flag booleana de desenvolvimento. Foi
  decidido não reescrever o histórico. Atualmente `.env` e `.env.local` estão
  ignorados, e `.env.example` é a referência versionada.
- A auditoria Maven com OWASP Dependency-Check não foi concluída porque a
  alimentação inicial da base NVD é muito pesada sem cache e chave de API. Uma
  segunda verificação com OSV Scanner conseguiu analisar o grafo Maven e
  encontrou as dependências descritas abaixo.

## 1. Acesso ao beta

### Situação atual

O endpoint `POST /auth/register` é público. Portanto, distribuir o aplicativo
apenas para um grupo pequeno não torna, por si só, o backend privado: qualquer
pessoa que descubra a URL da API pode tentar criar uma conta. O rate limiting
reduz abuso automatizado, mas não transforma o cadastro em um fluxo por convite.
CORS também não é controle de acesso, pois não impede chamadas diretas por
scripts ou outros clientes.

### Alternativas

#### Cadastro por convite ou allowlist — recomendação para o beta

- **Desenvolvimento:** exige criar e validar convites ou manter uma lista de
  e-mails autorizados. Também exige definir expiração, reutilização e o fluxo de
  suporte para convites inválidos.
- **Produto:** oferece uma entrada clara para testadores e permite ampliar o
  beta gradualmente, sem precisar republicar ou reconfigurar a aplicação.
- **Risco residual:** convites podem ser compartilhados ou contas podem ser
  comprometidas; ainda são necessários rate limiting e monitoramento.

#### Desabilitar o cadastro após criar as contas de teste

- **Desenvolvimento:** é a solução mais simples, mas precisa de configuração por
  ambiente e de um meio administrativo seguro para criar novos testadores.
- **Produto:** dificulta aumentar o grupo e torna a entrada de cada participante
  uma operação manual.
- **Risco residual:** reduz bastante cadastros não autorizados, mas não protege
  a própria URL contra sondagem, ataques aos endpoints públicos ou contas
  comprometidas.

#### Proteger todo o ambiente na infraestrutura

Exemplos incluem VPN, allowlist de IP, autenticação adicional no proxy ou uma
plataforma de preview com controle de acesso.

- **Desenvolvimento:** evita alterar imediatamente o fluxo de cadastro, mas
  transfere a configuração para a infraestrutura e pode dificultar testes em
  celulares e redes móveis.
- **Produto:** funciona bem para testes internos; costuma ser inconveniente para
  um beta com participantes externos.
- **Risco residual:** uma configuração incorreta do proxy pode expor toda a API.

### Risco de manter como está

O beta será privado apenas por obscuridade e distribuição limitada do cliente,
não por uma regra aplicada pelo servidor. Para uma URL acessível pela internet,
isso não é considerado um controle suficiente.

## 2. Dependências e linha do Spring Boot

### Situação atual

O backend usa Spring Boot 4.0.6. O `pom.xml` também fixa
`spring-boot-starter-aop` em `4.0.0-M2` e `jackson-databind` em 2.21.3, impedindo
que essas duas dependências acompanhem integralmente as versões gerenciadas pelo
parent.

O OSV Scanner encontrou 44 correspondências de advisories em 11 pacotes. Esse
número contém repetições entre Jackson 2 e 3 e falhas condicionais; não significa
que existam 44 caminhos exploráveis no BeeFit. Os grupos relevantes são:

| Componente atual | Principais riscos | Versão mínima considerada na correção |
| --- | --- | --- |
| Tomcat 11.0.21 | autenticação DIGEST incorreta, headers HTTP/2 sem validação e aplicação incorreta de certas restrições | 11.0.22 |
| Spring Framework 7.0.7 | DoS por SpEL, XSS em `JavaScriptUtils` e DoS em recursos versionados | 7.0.8 |
| Spring Data Commons 4.0.5 | crescimento ilimitado de cache de caminhos de propriedades | 4.0.6 |
| PostgreSQL JDBC 42.7.10 | DoS na autenticação SCRAM e downgrade de channel binding | 42.7.12 |
| Jackson 2.21.2/2.21.3 | DoS no parser e bypasses de validação polimórfica | 2.21.5 para eliminar também os achados médios observados |
| Jackson 3.1.2 | riscos equivalentes no parser e na desserialização | 3.1.5 para eliminar também os achados médios observados |
| Logback 1.5.32 | achados de baixa gravidade | 1.5.34 |

Os caminhos críticos de DIGEST, SpEL fornecida pelo usuário, parser assíncrono
e desserialização polimórfica não foram encontrados na implementação atual. Em
contrapartida, Tomcat está na entrada de todas as requisições, e o projeto aceita
paginação e ordenação em endpoints que usam Spring Data. As correções continuam
recomendadas mesmo quando a exploração atual parece improvável.

### Alternativa A — manter Spring Boot 4.0.6

- **Desenvolvimento:** não exige migração imediata, mas demanda overrides
  individuais para todos os componentes corrigidos. Isso aumenta a distância do
  BOM testado pelo Spring Boot e o trabalho de acompanhar compatibilidade.
- **Produto:** não produz benefício funcional perceptível para o usuário.
- **Risco:** mantém versões com vulnerabilidades conhecidas no runtime caso os
  overrides não sejam aplicados. Uma futura mudança de configuração pode ativar
  um caminho hoje não utilizado.

### Alternativa B — atualizar para Spring Boot 4.0.7 — recomendação

O Spring Boot 4.0.7 já gerencia Tomcat 11.0.22, Spring Framework 7.0.8, Spring
Data Commons 4.0.6, Jackson 2.21.4/3.1.4 e Logback 1.5.34.

- **Desenvolvimento:** é uma atualização de manutenção na mesma linha. É
  necessário remover a versão `4.0.0-M2` do starter AOP, remover ou atualizar o
  pin de Jackson e executar novamente testes, JaCoCo e o scanner.
- **Produto:** não deve introduzir mudança funcional intencional; o objetivo é
  corrigir infraestrutura e segurança.
- **Complementos ainda necessários:** PostgreSQL JDBC 42.7.12 não é fornecido
  pelo BOM 4.0.7. Para zerar também os achados médios observados, Jackson precisa
  ser validado nas versões 2.21.5 e 3.1.5.
- **Risco:** baixo em comparação com uma mudança de linha, mas não zero. O grafo
  de dependências e a inicialização do contexto precisam ser revalidados.

### Alternativa C — migrar para Spring Boot 4.1.0

O Spring Boot 4.1.0 atualiza Spring Data para 4.1.0 e também incorpora Tomcat
11.0.22, Spring Framework 7.0.8, Jackson 2.21.4/3.1.4 e Logback 1.5.34. Porém,
ele ainda gerencia PostgreSQL JDBC 42.7.11 e, portanto, não elimina sozinho
todos os achados.

- **Desenvolvimento:** é uma atualização de linha, não apenas de manutenção.
  APIs anteriormente depreciadas podem ter sido removidas e o salto de Spring
  Data exige testes mais amplos de repositórios, paginação e persistência.
- **Produto:** não existe hoje uma funcionalidade do beta que justifique essa
  migração por si só.
- **Risco:** maior possibilidade de regressão sem resolver integralmente a
  auditoria; ainda seriam necessários ajustes para PostgreSQL e possivelmente
  Jackson.

### Risco de manter como está

É provável que o backend continue funcionando, mas a imagem publicada conterá
componentes sinalizados com gravidade crítica e alta. Mesmo quando o BeeFit não
usa a configuração vulnerável, isso aumenta o risco de segurança, gera alertas
em plataformas de hospedagem e torna uma mudança futura de configuração mais
perigosa.

Referências:

- [Dependências gerenciadas pelo Spring Boot 4.0.7](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html)
- [Dependências gerenciadas pelo Spring Boot 4.1.0](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- [Tomcat: validação de headers HTTP/2](https://github.com/advisories/GHSA-r29c-68gh-xp6x)
- [Spring Data: cache ilimitado](https://github.com/advisories/GHSA-88fw-v6x4-3f58)
- [PostgreSQL JDBC: channel binding](https://github.com/advisories/GHSA-j92g-9f8w-j867)

## 3. CI e deploy

A escolha da solução de CI e deploy foi deliberadamente adiada para uma análise
posterior. Nenhum workflow do GitHub Actions foi criado.

### CI não implica deploy automático

É possível usar GitHub Actions apenas para validar pull requests, sem publicar
nada. Um pipeline de CI poderia executar instalação limpa, testes, build, lint,
JaCoCo, auditoria de dependências e varredura de segredos. O deploy pode continuar
manual ou ser automatizado separadamente mais tarde.

### Alternativas a estudar

#### CI no GitHub Actions, deploy manual

- **Desenvolvimento:** feedback automático e reproduzível antes de integrar
  mudanças; exige configurar cache, Java 25, Node, Docker/Testcontainers e
  credenciais do scanner de dependências.
- **Produto:** reduz regressões sem automatizar uma publicação acidental.
- **Risco de adiar:** uma mudança pode ser integrada sem que todos os checks
  locais tenham sido executados.

#### CI e deploy automatizados

- **Desenvolvimento:** reduz tarefas manuais, mas exige definir ambientes,
  aprovações, rollback, migrations, segredos e branch protegida.
- **Produto:** entregas ficam mais rápidas e consistentes.
- **Risco:** automação incorreta pode publicar uma versão, executar migrations
  ou usar segredos no ambiente errado. O deploy de produção deve ter aprovação
  e estratégia de rollback.

#### Processo inteiramente manual durante o beta

- **Desenvolvimento:** menor custo inicial, com uma checklist executada por uma
  pessoa antes de cada build.
- **Produto:** aceitável para pouquíssimas versões controladas.
- **Risco:** depende de memória e disciplina; os resultados não ficam associados
  automaticamente a cada commit.

### Gates que a solução futura deve contemplar

- frontend: `npm ci`, `npm run lint`, `npm run test:run`, `npm run build` e
  `npm audit`;
- backend: `./mvnw verify`, incluindo PostgreSQL/Testcontainers e JaCoCo;
- auditoria de dependências Maven com cache e fonte de dados confiável;
- Gitleaks ou ferramenta equivalente no conteúdo atual e no histórico;
- proteção de branch e checks obrigatórios, caso o fluxo de trabalho adote pull
  requests;
- separação de credenciais e aprovações entre homologação e produção.

## 4. Configuração e operação do ambiente

Antes de qualquer deploy com perfil `prod`, é necessário:

- fornecer um `JWT_SECRET` forte e exclusivo por ambiente;
- fornecer a conexão PostgreSQL por configuração externa;
- definir `BEEFIT_SECURITY_CORS_ALLOWED_ORIGINS` com origens HTTPS exatas e sem
  curingas;
- colocar a aplicação atrás de HTTPS e configurar corretamente os headers
  encaminhados pelo proxy; do contrário, o filtro de produção responde
  `426 HTTPS_REQUIRED`;
- executar e acompanhar as migrations Flyway V1 a V5;
- gerar o frontend com `VITE_API_URL` apontando para a API HTTPS correta e sem
  `VITE_DEV_AUTH_BYPASS`;
- configurar logs sem dados sensíveis, métricas e alertas para autenticação,
  upload, finalização de treino e latência;
- definir backup, restauração e resposta a incidentes.

As mídias são armazenadas como bytes no PostgreSQL. Isso simplifica a
consistência e o controle de acesso no beta, mas aumenta crescimento, tráfego de
backup e custo do banco. Capacidade, retenção e restauração de imagens precisam
ser consideradas no dimensionamento.

### Risco de configuração incorreta

O backend pode não iniciar sem os segredos e o banco, rejeitar todo o frontend
por CORS, responder 426 atrás de um proxy mal configurado ou apontar o cliente
para a API errada. Sem backup e monitoramento, uma falha pode ser percebida
somente pelos testadores e resultar em perda de dados do beta.

## 5. Uploads, dados e privacidade

O servidor já valida o conteúdo real da imagem, JPEG/PNG, tamanho e dimensões.
Ainda falta:

- validar e normalizar o nome de arquivo;
- remover metadados EXIF;
- aplicar quotas por usuário e concluir a política de retenção;
- garantir a exclusão de mídias, sessões, tokens e dados derivados ao excluir
  uma conta;
- testar arquivos adulterados, imagens malformadas, dimensões extremas e acesso
  à mídia de outro usuário;
- revisar política de privacidade e termos contra os dados realmente coletados;
- validar o fluxo completo de exclusão de conta e confirmação de senha.

### Impacto e risco

Esses trabalhos aumentam esforço no backend e exigem decisões de produto sobre
retenção e exclusão. Manter como está pode preservar metadados desnecessários,
permitir crescimento descontrolado do banco e produzir uma divergência entre a
política apresentada ao usuário e o comportamento real do sistema.

## 6. Moderação e operação social

Ainda é necessário implementar ou definir:

- denúncia e bloqueio de usuários;
- remoção de conteúdo e processo de revisão;
- trilha de auditoria operacional;
- retenção de fotos, logs, sessões e dados de treino.

### Impacto e risco

Esses recursos adicionam fluxos administrativos e regras de autorização, mas
são importantes mesmo em um beta fechado. Sem eles, incidentes sociais ou
conteúdo impróprio dependerão de intervenção direta no banco e terão pouca
rastreabilidade.

## 7. Experiência e resiliência

Pendências:

- padronizar estados de loading, vazio, erro, retry e sucesso;
- testar rede lenta, offline, retomada do app, botão voltar, deep links, rotação
  e orientação paisagem;
- expandir a recuperação de treino pendente para testes E2E em navegador e
  dispositivos reais;
- testar telas pequenas, tablets, TalkBack e VoiceOver;
- reduzir o bundle inicial, atualmente próximo de 1,36 MB, e medir abertura em
  aparelhos reais;
- revisar os 7 warnings restantes do lint.

### Impacto e risco

Essas pendências não necessariamente impedem um beta interno, mas afetam a
qualidade do feedback. Falhas de rede ou retomada podem ser confundidas com
problemas funcionais, e limitações de acessibilidade excluem parte dos possíveis
testadores.

## 8. Validação nativa

Antes de distribuir builds móveis:

- executar `npm run mobile:sync` após as mudanças web relevantes;
- gerar builds release Android e iOS;
- testar em aparelhos reais login, refresh, logout, câmera, galeria, upload,
  treino ativo, retomada e funcionamento offline;
- validar Android release com R8/ProGuard e corrigir regras dos plugins Capacitor;
- confirmar Secure Storage de tokens no Android e iOS;
- validar permissões, ícones, splash screen, assinatura Android, certificados
  iOS e deep links;
- preencher as declarações de dados e privacidade da Google Play e App Store
  quando a distribuição usar essas lojas.

### Impacto e risco

O build web passando não garante que plugins nativos, permissões, câmera,
armazenamento seguro ou minificação funcionem no binário release. Distribuir sem
esses testes pode produzir falhas que não aparecem no navegador.

## Critério sugerido para iniciar o beta

Antes de abrir o ambiente aos testadores:

1. restringir tecnicamente o cadastro ou proteger todo o ambiente;
2. atualizar as dependências críticas/altas e repetir `./mvnw verify` e a
   auditoria;
3. configurar HTTPS, proxy, CORS, segredos, banco e backup;
4. executar manualmente todos os gates enquanto não houver CI;
5. testar os builds release nos dispositivos que serão usados no beta;
6. possuir um procedimento mínimo para suporte, remoção de conteúdo, restauração
   de dados e encerramento do acesso de um testador.

Para uma publicação pública, as pendências de privacidade, moderação,
observabilidade, retenção, acessibilidade e lojas deixam de ser apenas melhorias
do beta e passam a ser requisitos de lançamento.
