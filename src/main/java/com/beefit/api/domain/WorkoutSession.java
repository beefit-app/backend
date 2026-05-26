package com.beefit.api.domain;

import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.WorkoutStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workout_sessions")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "estimated_calories")
    private Double estimatedCalories;

    @Column(name = "total_volume")
    private Double totalVolume;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "muscle_groups", columnDefinition = "varchar[]")
    private List<MuscleGroups> muscleGroupsList;

    @NotNull
    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkoutStatus status;

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order ASC") // Garante que ao dar getExercises(), eles venham na ordem
    private List<SessionExercise> exercises = new ArrayList<>();

    // Método auxiliar (Helper) para garantir que o relacionamento bidirecional
    // funcione perfeitamente no Java
    public void addExercise(SessionExercise exercise) {
        this.exercises.add(exercise);
        exercise.setWorkout(this); // Seta o lado inverso da relação automaticamente
    }

    public void removeExercise(SessionExercise exercise) {
        this.exercises.remove(exercise);
        exercise.setWorkout(null);
    }

}
