package com.beefit.api.controller;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.dto.CardFilterDTO;
import com.beefit.api.dto.InventoryCardResponseDTO;
import com.beefit.api.exception.InvalidCardRarityException;
import com.beefit.api.exception.InvalidMuscleGroupException;
import com.beefit.api.service.InventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<Page<InventoryCardResponseDTO>> getUserCards(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String rarity,
            @RequestParam(required = false) String muscleGroup,
            @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        CardFilterDTO filters = new CardFilterDTO(parseRarity(rarity), parseMuscleGroup(muscleGroup));
        Page<InventoryCardResponseDTO> cards = inventoryService.getUserCards(user.getId(), filters, pageable);
        return ResponseEntity.ok(cards);
    }

    private CardRarity parseRarity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = normalize(value);
        return switch (normalized) {
            case "COMMON", "COMUM" -> CardRarity.COMMON;
            case "RARE", "RARA", "RARO" -> CardRarity.RARE;
            case "EPIC", "EPICA", "EPICO" -> CardRarity.EPIC;
            case "LEGENDARY", "LENDARIA", "LENDARIO" -> CardRarity.LEGENDARY;
            default -> throw new InvalidCardRarityException(value);
        };
    }

    private MuscleGroups parseMuscleGroup(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = normalize(value);
        return switch (normalized) {
            case "CHEST", "PEITO", "PEITORAL" -> MuscleGroups.CHEST;
            case "BACK", "COSTAS", "DORSAL", "DORSAIS" -> MuscleGroups.BACK;
            case "LEGS", "PERNA", "PERNAS", "QUADRICEPS" -> MuscleGroups.LEGS;
            case "GLUTES", "GLUTEOS" -> MuscleGroups.GLUTES;
            case "SHOULDERS", "OMBROS" -> MuscleGroups.SHOULDERS;
            case "ARMS", "BRACOS", "BICEPS", "TRICEPS" -> MuscleGroups.ARMS;
            case "CORE", "ABDOMEN", "ABDOMINAIS" -> MuscleGroups.CORE;
            case "TRAPS", "TRAPEZIO", "TRAPEZIUS" -> MuscleGroups.TRAPS;
            default -> throw new InvalidMuscleGroupException(value);
        };
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("-", "_")
                .toUpperCase(Locale.ROOT);
    }
}
