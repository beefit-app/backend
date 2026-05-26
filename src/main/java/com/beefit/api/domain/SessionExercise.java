package com.beefit.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "session_exercises")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SessionExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workout_id", nullable = false)
    private WorkoutSession workout;

    @ManyToOne
    @JoinColumn(name = "exercise_id", nullable = false)
    private CatalogExercise exercise; // Vem do catálogo cacheado da Wger

    @Column(name = "exercise_order", nullable = false)
    private Integer order; // Ordem do Exercício no treino

    // Uma lista ordenada de séries
    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order ASC") // Garante que a série 1 venha antes da série 2
    private List<Set> sets = new ArrayList<>();

    public void addSet(Set set) {
        this.sets.add(set);
        set.setExercise(this);
    }
}
