package com.beefit.api.domain.enums;

import lombok.Getter;

@Getter
public enum CardRarity {

    COMMON("Comum"),
    RARE("Raro"),
    EPIC("Épico"),
    LEGENDARY("Lendário");

    final String rarity;

    CardRarity(String rarity) {
        this.rarity = rarity;
    }

}
