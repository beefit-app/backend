package com.beefit.api.dto.auth;

import jakarta.validation.constraints.*;

// As mensagens vao para ErrorResponse.fields e sao exibidas embaixo do campo
// correspondente na tela de cadastro, entao sao escritas para o usuario final.
public record RegisterDTO(@NotBlank(message = "Informe seu nome completo.")
                          @Size(min = 4, max = 100, message = "O nome deve ter de 4 a 100 caracteres.")
                          String fullName,

                          @NotBlank(message = "Escolha um nome de usuário.")
                          @Size(max = 255, message = "O nome de usuário deve ter no máximo 255 caracteres.")
                          String username,

                          @NotBlank(message = "Informe seu e-mail.")
                          @Email(message = "Informe um e-mail válido, como nome@email.com.")
                          @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
                          String email,

                          @NotBlank(message = "Crie uma senha.")
                          @Size(min = 8, max = 64, message = "A senha deve ter de 8 a 64 caracteres.")
                          @Pattern(
                                  regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                                  message = "A senha deve incluir pelo menos uma letra e um número.")
                          String password,

                          @NotNull(message = "Informe seu peso.")
                          @Min(value = 30, message = "O peso deve ser de 30 a 300 kg.")
                          @Max(value = 300, message = "O peso deve ser de 30 a 300 kg.")
                          Float bodyWeight,

                          @NotNull(message = "Informe sua meta semanal.")
                          @Min(value = 1, message = "A meta semanal deve ser de 1 a 7 dias.")
                          @Max(value = 7, message = "A meta semanal deve ser de 1 a 7 dias.")
                          Integer weeklyGoal,

                          // Preenchido pelo app, nao pelo usuario: se falhar, nao ha campo
                          // na tela para marcar e a mensagem cai no aviso geral.
                          @NotBlank(message = "Requisição sem identificação do dispositivo.")
                          @Size(max = 255, message = "A identificação do dispositivo deve ter no máximo 255 caracteres.")
                          String deviceId,

                          @NotBlank(message = "Aceite os Termos de Uso vigentes.")
                          @Size(max = 64, message = "A versão dos Termos de Uso é inválida.")
                          String acceptedTermsVersion,

                          @NotBlank(message = "Aceite a Política de Privacidade vigente.")
                          @Size(max = 64, message = "A versão da Política de Privacidade é inválida.")
                          String acceptedPrivacyVersion) {
}
