package com.beefit.api.domain;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sets")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Set {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private SessionExercise exercise;

    @NotNull
    @PositiveOrZero
    @Column(name = "weight_kg", nullable = false)
    private Double weightKg = 0.0;

    @NotNull
    @Column(name = "set_order", nullable = false)
    private Integer order;

    @NotNull
    @PositiveOrZero
    private Integer reps = 0;

    @NotNull
    @Column(name = "is_personal_record", nullable = false)
    private Boolean isPersonalRecord;
}
