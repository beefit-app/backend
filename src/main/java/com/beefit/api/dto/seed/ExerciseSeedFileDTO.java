package com.beefit.api.dto.seed;

import lombok.Getter;

import java.util.List;

@Getter
public class ExerciseSeedFileDTO {

    private String name;
    private String description;
    private int count;
    private List<ExerciseSeedDTO> exercises;
}