package com.beefit.api.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(min = 4, max = 100)
    @Column(nullable = false, length = 100)
    private String fullName;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotNull
    @Positive
    @Column(name = "body_weight", nullable = false)
    private Float bodyWeight;

    @Column(name = "profile_picture",nullable = true)
    private String profilePicture;

    @Column(nullable = true, length = 500)
    private String biography;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String username;

    @NotNull
    @Column(name = "ghost_mode", nullable = false)
    private Boolean ghostMode = false; // Desativado por padrão

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer level = 1; // User começa nível 1

    @NotNull
    @Min(1)
    @Max(7)
    @Column(name = "weekly_goal", nullable = false)
    private Integer weeklyGoal;

    @NotNull
    @Min(0)
    @Column(name = "offensive_honeys", nullable = false)
    private Integer offensiveHoneys = 0; // Começa zerado

    @NotBlank
    @Column(nullable = false)
    private String role; // USER ou ADMIN
}
