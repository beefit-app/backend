package com.beefit.api.domain.enums;

import lombok.Getter;

@Getter
public enum UserRole {
    ADMIN("admin"),
    DEVELOPER("developer"),
    USER("user");

    // Estas estruturas extras no enum (atributo role, construtor e getter)
    // são para associarmos a string personalizada na constante
    // Depois, conseguimos receber o valor dessa string com: User.ADMIN.getRole()

    final String role;

    UserRole(String role) {
        this.role = role;
    }
}
