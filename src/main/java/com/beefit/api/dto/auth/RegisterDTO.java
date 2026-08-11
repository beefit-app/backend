package com.beefit.api.dto.auth;

import jakarta.validation.constraints.*;

// As mensagens vao para ErrorResponse.fields e sao exibidas embaixo do campo
// correspondente na tela de cadastro, entao sao escritas para o usuario final.
public record RegisterDTO(@NotBlank(message = "Informe seu nome completo.") String fullName,

                          @NotBlank(message = "Escolha um nome de usuário.") String username,

                          @NotBlank(message = "Informe seu e-mail.")
                          @Email(message = "Informe um e-mail válido, como nome@email.com.")
                          String email,

                          // Lembrar de mexer depois
                          /*@Pattern(
                                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
                                message = "A senha deve ter no mínimo 8 caracteres, incluindo 1 maiúscula, 1 minúscula, 1 número e 1 caractere especial."
                          )*/
                          @NotBlank(message = "Crie uma senha.")
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
                          String deviceId) {
}
