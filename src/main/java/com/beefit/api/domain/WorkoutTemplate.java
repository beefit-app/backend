package com.beefit.api.domain;


import com.beefit.api.domain.enums.MuscleGroups;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
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

    @OneToMany(mappedBy = "workoutTemplate", orphanRemoval = true)
    @OrderBy("order ASC")
    private List<TemplateExercise> exercises = new ArrayList<>();

    @OneToOne
    @JoinColumn(name = "friend_source_id")
    private User friendSource;
}
