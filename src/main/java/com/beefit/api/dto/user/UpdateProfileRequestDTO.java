package com.beefit.api.dto.user;

import jakarta.validation.constraints.*;

// Mesmas restricoes de peso do RegisterDTO: cadastro e edicao de perfil gravam o
// mesmo campo, entao divergir aqui deixaria uma rota aceitar o que a outra recusa.
public record UpdateProfileRequestDTO(
        @NotBlank(message = "Informe seu nome completo.")
        @Size(min = 4, max = 100, message = "O nome deve ter de 4 a 100 caracteres.")
        String fullName,

        @Size(max = 500, message = "A biografia deve ter no máximo 500 caracteres.")
        String biography,

        String profilePicture,

        @NotNull(message = "Informe seu peso.")
        @Min(value = 30, message = "O peso deve ser de 30 a 300 kg.")
        @Max(value = 300, message = "O peso deve ser de 30 a 300 kg.")
        Float bodyWeight
) {}