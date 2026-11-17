package com.beefit.api.dto.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Mesmas regras/mensagens do RegisterDTO para os campos equivalentes.
public record CompleteProfileRequestDTO(
        @NotNull(message = "Informe seu peso.")
        @Min(value = 30, message = "O peso deve ser de 30 a 300 kg.")
        @Max(value = 300, message = "O peso deve ser de 30 a 300 kg.")
        Float bodyWeight,

        @NotNull(message = "Informe sua meta semanal.")
        @Min(value = 1, message = "A meta semanal deve ser de 1 a 7 dias.")
        @Max(value = 7, message = "A meta semanal deve ser de 1 a 7 dias.")
        Integer weeklyGoal,

        @NotBlank(message = "Aceite os Termos de Uso vigentes.")
        @Size(max = 64, message = "A versão dos Termos de Uso é inválida.")
        String acceptedTermsVersion,

        @NotBlank(message = "Aceite a Política de Privacidade vigente.")
        @Size(max = 64, message = "A versão da Política de Privacidade é inválida.")
        String acceptedPrivacyVersion) {
}
