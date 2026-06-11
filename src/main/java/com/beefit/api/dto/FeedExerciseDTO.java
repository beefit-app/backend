package com.beefit.api.dto;

import java.util.List;
import java.util.UUID;


 // DTO que detalha um exercício específico realizado dentro de uma publicação do Feed.

public record FeedExerciseDTO(
        UUID exerciseId,        // ID do exercício no catálogo (vindo do Wger ou do dataset que o Ryan encontrou)
        String exerciseName,    // Nome legível do exercício (ex: "Supino Reto")
        Integer order,          // Ordem sequencial do exercício definida na sessão


         //Lista ordenada contendo todas as séries (carga e repetições) completadas neste exercício.

        List<FeedSetDTO> sets
) {}