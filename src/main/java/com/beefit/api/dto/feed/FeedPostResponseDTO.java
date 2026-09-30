package com.beefit.api.dto.feed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


 // DTO que representa a estrutura completa de uma publicação no Feed.
 // Agrega dados do perfil do usuário, metadados sociais e o resumo do treino realizado.

public record FeedPostResponseDTO(
        // Identificadores únicos do post e do autor
        UUID postId,
        UUID userId,

        // Sessão que originou o post. O cliente precisa dela para apagar o treino:
        // DELETE /api/workout/{id} opera sobre a sessão, não sobre o post.
        UUID workoutSessionId,

        // Dados de exibição do Perfil do Usuário
        String username,
        String userProfilePicture,
        Boolean developer,

        // Metadados do Post (Sociais)
        LocalDateTime publishedAt,
        Integer totalLikes,
        Boolean likedByCurrentUser,
        String title,
        String photoUrl,

        // Dados Agregados do Treino (Extraídos da WorkoutSession)
        Integer durationSeconds,       // Tempo total cronometrado do treino
        Double estimatedCalories,     // Calorias calculadas dinamicamente via fórmula MET


         //Mapa de Calor Corporal
         // O Front-end usará esses nomes (ex: "CHEST", "BACK") para colorir o modelo anatômico na tela.

        List<String> bodyHeatMap,


         //Lista detalhada contendo os exercícios, séries e cargas realizados nesta sessão.

        List<FeedExerciseDTO> exercises
) {}
