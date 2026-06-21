package com.beefit.api.dto.feed;

import java.util.UUID;


 // DTO que representa uma série individual (set) de um exercício exibido no Feed.

public record FeedSetDTO(
        UUID setId,          // ID único da série no banco de dados
        Integer order,       // Número sequencial da série (ex: Série 1, Série 2)
        Double weightKg,     // Carga utilizada na série em Quilogramas
        Integer reps         // Quantidade de repetições executadas com sucesso
) {}