package com.beefit.api.domain;

import com.beefit.api.domain.enums.MuscleGroups;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "exercises")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CatalogExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    private String name;

    // Essa anotações fazem com que seja possível salvar uma lista de varchar diretamente no db postgres
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "primary_muscle_groups", columnDefinition = "varchar array")
    private List<MuscleGroups> primaryMuscleGroupsList;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "secondary_muscle_groups", columnDefinition = "varchar array")
    private List<MuscleGroups> secondaryMuscleGroupsList;

    @Column(length = 1000)
    private String instructions;
}
