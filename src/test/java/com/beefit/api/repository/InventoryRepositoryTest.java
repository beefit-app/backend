package com.beefit.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.beefit.api.domain.Card;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.support.PostgreSqlTestSupport;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class InventoryRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport
            .createContainer("beefit_inventory_test");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findCardsWithFiltersReturnsOnlyCardsFromUserInventory() {
        User owner = persistUser("owner");
        User other = persistUser("other");
        Inventory ownerInventory = persistInventory(owner);
        Inventory otherInventory = persistInventory(other);
        Card ownerCard = persistCard(ownerInventory, owner, CardRarity.COMMON, List.of(MuscleGroups.CHEST));
        persistCard(otherInventory, other, CardRarity.COMMON, List.of(MuscleGroups.CHEST));
        entityManager.flush();
        entityManager.clear();

        Page<Card> result = inventoryRepository.findCardsWithFilters(
                owner.getId(), null, null, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(ownerCard.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void findCardsWithFiltersFiltersByRarityAndMuscleGroup() {
        User owner = persistUser("owner");
        Inventory inventory = persistInventory(owner);
        Card matching = persistCard(inventory, owner, CardRarity.RARE, List.of(MuscleGroups.CHEST, MuscleGroups.ARMS));
        persistCard(inventory, owner, CardRarity.COMMON, List.of(MuscleGroups.CHEST));
        persistCard(inventory, owner, CardRarity.RARE, List.of(MuscleGroups.LEGS));
        entityManager.flush();
        entityManager.clear();

        Page<Card> result = inventoryRepository.findCardsWithFilters(
                owner.getId(), MuscleGroups.CHEST, CardRarity.RARE, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(matching.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void findCardsWithFiltersPaginatesFilteredResults() {
        User owner = persistUser("owner");
        Inventory inventory = persistInventory(owner);
        persistCard(inventory, owner, CardRarity.EPIC, List.of(MuscleGroups.BACK));
        persistCard(inventory, owner, CardRarity.EPIC, List.of(MuscleGroups.BACK));
        persistCard(inventory, owner, CardRarity.COMMON, List.of(MuscleGroups.BACK));
        entityManager.flush();
        entityManager.clear();

        Page<Card> result = inventoryRepository.findCardsWithFilters(
                owner.getId(), MuscleGroups.BACK, CardRarity.EPIC, PageRequest.of(0, 1));

        assertEquals(2, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    private User persistUser(String username) {
        User user = new User();
        user.setFullName(username + " User");
        user.setEmail(username + "@beefit.test");
        user.setPasswordHash("password-hash");
        user.setBodyWeight(80.0F);
        user.setUsername(username + "-" + UUID.randomUUID());
        user.setGhostMode(false);
        user.setWeeklyGoal(3);
        user.setWorkoutsCount(0);
        user.setWeeklyWorkoutsCount(0);
        user.setCardsCount(0);
        user.setOffensiveHoneys(0);
        user.setRole(UserRole.USER);
        entityManager.persist(user);
        return user;
    }

    private Inventory persistInventory(User user) {
        Inventory inventory = new Inventory();
        inventory.setUser(user);
        inventory.setTotalCards(0);
        entityManager.persist(inventory);
        return inventory;
    }

    private Card persistCard(Inventory inventory, User user, CardRarity rarity, List<MuscleGroups> muscleGroups) {
        WorkoutSession workoutSession = new WorkoutSession();
        workoutSession.setUser(user);
        workoutSession.setStartedAt(LocalDateTime.now().minusHours(1));
        workoutSession.setFinishedAt(LocalDateTime.now());
        workoutSession.setStatus(WorkoutStatus.FINISHED);
        workoutSession.setShouldPostWorkout(false);
        workoutSession.setIsPrivate(false);
        workoutSession.setMuscleGroupsList(muscleGroups);
        entityManager.persist(workoutSession);

        Card card = new Card();
        card.setInventory(inventory);
        card.setWorkoutSession(workoutSession);
        card.setAttackPower(100.0);
        card.setCardRarity(rarity);
        card.setMuscleGroup(muscleGroups.getFirst());
        card.setIsPersonalRecord(false);
        card.setBodyWeightAtGeneration(user.getBodyWeight().doubleValue());
        entityManager.persist(card);
        return card;
    }
}
