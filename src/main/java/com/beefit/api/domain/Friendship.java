package com.beefit.api.domain;

import com.beefit.api.domain.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
// A unicidade do par de usuários, independentemente da direção do convite,
// é garantida pelo índice funcional criado pelo Flyway.
@Table(name = "friendship")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
// Essa é a tabela intemediária entre usuários que contém convites de amizade e amizades aceitas
// Resolvi unir ambas em uma entidade só pela facilidade de operações e custo computacional
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    // JoinColumn serve para definir qual nome vai para a coluna dessa fk no database
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id")
    private User addressee;

    @Enumerated(EnumType.STRING)
    private FriendshipStatus status;

    // Esta anotação faz com que o framework atribua o tempo atual sozinho no momento da criação da entidade
    @CreationTimestamp
    private LocalDateTime sentAt;

    // Já esta anotação faz a mesma coisa, mas quando a entidade foi editada
    // A ideia é registrar exatamente quando os usuários viraram amigos
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
