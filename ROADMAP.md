# Roadmap e Otimizações Futuras (Backend)

Este documento lista as melhorias arquiteturais e de banco de dados planejadas para o servidor.

## 1. Enriquecimento do Catálogo de Exercícios (Pré-requisito Frontend)
- **Objetivo:** Melhorar a qualidade da base de dados de exercícios antes de permitirmos que o Frontend faça o download completo e offline desse catálogo.
- **Ações:**
  - Curadoria e adição de exercícios mais completos no arquivo `seeds/exercises.json`.
  - Melhorar as descrições, revisar os mapeamentos anatômicos (músculos sinergistas/alvos) e possivelmente adicionar suporte estrutural para vídeos/GIFs de execução curados.
  - Criar um endpoint de versionamento do catálogo (`/api/catalog/version`) para que o aplicativo saiba quando deve baixar uma base de dados nova.

## 2. Otimização de Mídia e Armazenamento
- **Objetivo:** Suportar a migração do processamento pesado de mídia para o celular do usuário.
- **Ações:**
  - Preparar o endpoint de upload para aceitar e otimizar nativamente a persistência do formato `WebP` (que o frontend passará a gerar).

## Decisões Arquiteturais Consolidadas
- **Server-Sent Events (SSE) e Presença:** A funcionalidade de rastrear o início do treino no backend (`POST /api/workout/start`) será **mantida**. É vital que o servidor receba esse evento para distribuir a notificação em tempo real de que o usuário "está treinando" para seus amigos.
- **Cálculo de Gamificação:** O processamento das porcentagens de progressão para as Cartas e Ofensiva continua sendo estritamente validado no servidor para garantir confiabilidade.
