package com.beefit.api.domain.enums;

import lombok.Getter;

@Getter
public enum WorkoutStatus {
    IN_PROGRESS("in_progress"),
    FINISHED("finished");

    final String status;

    WorkoutStatus(String status){
        this.status = status;
    }
}
