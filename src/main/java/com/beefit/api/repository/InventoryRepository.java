package com.beefit.api.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.Card;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i LEFT JOIN FETCH i.cards WHERE i.user.id = :userId")
    Optional<Inventory> findByUserIdWithCards(@Param("userId") UUID userId);

    @Query("SELECT COUNT(c) FROM Inventory i JOIN i.cards c WHERE i.user.id = :userId AND c.cardRarity = :rarity")
    long countCardsByRarity(@Param("userId") UUID userId, @Param("rarity") CardRarity rarity);

    @Query("""
            SELECT c FROM Card c
            WHERE c.inventory.user.id = :userId
            AND (:muscleGroup IS NULL OR c.muscleGroup = :muscleGroup)
            AND (:rarity IS NULL OR c.cardRarity = :rarity)
            """)
    Page<Card> findCardsWithFilters(@Param("userId") UUID userId, @Param("muscleGroup") MuscleGroups muscleGroup,
            @Param("rarity") CardRarity rarity, Pageable pageable);
}
