package com.beefit.api.repository;

import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.enums.CardRarity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    @Query("SELECT i FROM Inventory i LEFT JOIN FETCH i.cards WHERE i.user.id = :userId")
    Optional<Inventory> findByUserIdWithCards(@Param("userId") UUID userId);

    @Query("SELECT COUNT(c) FROM Inventory i JOIN i.cards c WHERE i.user.id = :userId AND c.cardRarity = :rarity")
    long countCardsByRarity(@Param("userId") UUID userId, @Param("rarity") CardRarity rarity);
}
