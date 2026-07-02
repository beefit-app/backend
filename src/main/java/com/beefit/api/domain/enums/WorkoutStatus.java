package com.beefit.api.domain.enums;

import lombok.Getter;

@Getter
public enum WorkoutStatus {
    IN_PROGRESS("in_progress"),
    FINISHED("finished"),
    CANCELED("canceled");

    final String status;

    WorkoutStatus(String status) {
        this.status = status;
    }
}
