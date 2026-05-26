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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="workout_templates")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class WorkoutTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "muscle_groups", columnDefinition = "varchar[]")
    private List<MuscleGroups> muscleGroupsList;

    @JoinTable(
            name = "workout_template_exercises",
            joinColumns = @JoinColumn(name = "workout_template_id"),
            inverseJoinColumns = @JoinColumn(name = "catalog_exercise_id")
    )
    @ManyToMany
    private List<CatalogExercise> exerciseList;

    @OneToOne
    @JoinColumn(name = "friend_source_id")
    private User friendSource;
}
