package com.beefit.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String adminUsername; // Quem fez a alteração

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String details; // Detalhes ou JSON com o antes/depois

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime timestamp;

}