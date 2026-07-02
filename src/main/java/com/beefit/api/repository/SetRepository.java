package com.beefit.api.repository;

import com.beefit.api.domain.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface SetRepository extends JpaRepository<Set, UUID> {

    @Query("SELECT COALESCE(SUM(s.weightKg * s.reps), 0.0) FROM Set s")
    Double calculateTotalPlatformVolume();

}
