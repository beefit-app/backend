package com.beefit.api.repository;

import com.beefit.api.domain.Set;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SetRepository extends JpaRepository<Set, UUID> {
}
