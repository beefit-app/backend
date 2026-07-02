package com.beefit.api.domain;

import com.beefit.api.domain.enums.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {

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
    @Max(300)
    @Column(name = "body_weight", nullable = false)
    private Float bodyWeight;

    @Column(name = "profile_picture")
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
    @Min(1)
    @Max(7)
    @Column(name = "weekly_goal", nullable = false)
    private Integer weeklyGoal;

    @PositiveOrZero
    @Column(name = "workouts_count", nullable = false)
    private Integer workoutsCount = 0;

    @PositiveOrZero
    @Column(name = "weekly_workouts_count", nullable = false)
    private Integer weeklyWorkoutsCount = 0;

    @PositiveOrZero
    @Column(name = "cards_count", nullable = false)
    private Integer cardsCount = 0;

    @NotNull
    @Min(0)
    @Column(name = "offensive_honeys", nullable = false)
    private Integer offensiveHoneys = 0; // Começa zerado

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role; // USER ou ADMIN

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;

    public boolean isBanned() {
        return this.bannedUntil != null && this.bannedUntil.isAfter(LocalDateTime.now());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if(this.role == UserRole.ADMIN) return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !this.isBanned();
    }

}
