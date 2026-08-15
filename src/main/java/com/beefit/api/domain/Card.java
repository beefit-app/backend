package com.beefit.api.domain;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    // Treino no qual a carta foi gerada
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workoutSession_id", nullable = false)
    private WorkoutSession workoutSession;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private CatalogExercise exercise;

    @NotNull
    @Column(name = "best_set_weight_kg", nullable = false)
    private Double bestSetWeightKg;

    @NotNull
    @Column(name = "best_set_reps", nullable = false)
    private Integer bestSetReps;

    @Column(name = "estimated_one_rep_max")
    private Double estimatedOneRepMax;

    @NotNull
    @Column(name = "improvement_percentage", nullable = false)
    private Double improvementPercentage;

    @NotNull
    @Enumerated(value = EnumType.STRING)
    private CardRarity cardRarity;

    @NotNull
    @Enumerated(value = EnumType.STRING)
    private MuscleGroups muscleGroup;

    @NotNull
    private Double bodyWeightAtGeneration;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "muscle_intensities", columnDefinition = "jsonb", nullable = false)
    private Map<String, Integer> muscleIntensities = new LinkedHashMap<>();

    @CreationTimestamp
    private LocalDateTime generatedAt;

}
