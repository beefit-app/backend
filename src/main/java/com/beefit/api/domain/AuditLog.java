package com.beefit.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User adminUsername; // Quem fez a alteração

    @Column(nullable = false)
    private String action; // Ação que ele realizou

    @Column(nullable = false)
    private String targetEntity; // Ex: "User", "Workout"

    private String targetId; // O ID do objeto que sofreu a alteração

    @Column(columnDefinition = "TEXT")
    private String details; // Detalhes ou JSON com o antes/depois

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime timestamp;

}