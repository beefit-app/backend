package com.beefit.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relacionamento com o dono do post
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Relacionamento com a sessão de treino que gerou este post.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSession workoutSession;

    // Vamos pensar se usaremos
    //@JdbcTypeCode(SqlTypes.JSON)
    //@Column(columnDefinition = "jsonb")
    //private String bodyHeatMap;

    // Cache do total de likes para evitar COUNT(*) toda vez que renderizar o feed
    private Integer totalLikes = 0;

    // Para quando a moderação deletar um post
    private Boolean isRemoved = false;

    @CreationTimestamp
    private LocalDateTime publishedAt;

    // Relacionamento bidirecional com os Likes (útil para exclusão em cascata)
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Like> likes = new ArrayList<>();

    // --- Métodos Auxiliares para Encapsulamento ---

    public void incrementLikes() {
        this.totalLikes++;
    }

    public void decrementLikes() {
        if (this.totalLikes > 0) {
            this.totalLikes--;
        }
    }
}