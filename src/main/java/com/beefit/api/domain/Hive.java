package com.beefit.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "hives")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Hive {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    // Código único ou hash para gerar o link de convite do grupo
    @Column(unique = true, nullable = false)
    private String inviteCode;

    // O período de disputa definido pelo criador
    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // Relacionamento com o usuário que criou a colmeia
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    // Tabela intermediária (hive_members) gerada automaticamente pelo Hibernate
    // Usar Set no lugar de List em @ManyToMany melhora a performance e impede usuários duplicados no mesmo grupo
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "hive_members",
            joinColumns = @JoinColumn(name = "hive_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> members = new HashSet<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    // --- Métodos Auxiliares ---

    public void addMember(User user) {
        this.members.add(user);
    }

    public void removeMember(User user) {
        this.members.remove(user);
    }
}