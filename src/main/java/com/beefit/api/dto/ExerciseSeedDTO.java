package com.beefit.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true) // Tem que deixar explícito pra ignorar os campos restantes
public class ExerciseSeedDTO {

    @JsonProperty("name")
    String name;

    @JsonProperty("primaryMuscles")
    List<String> primaryMuscleGroupList;

    @JsonProperty("secondaryMuscles")
    List<String> secondaryMuscleGroupList;

    @JsonProperty("instructions")
    List<String> instructions;

}
