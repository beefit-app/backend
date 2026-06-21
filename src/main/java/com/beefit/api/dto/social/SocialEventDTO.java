package com.beefit.api.dto.social;

import com.beefit.api.domain.enums.SocialEvent;

public record SocialEventDTO<T>(
        SocialEvent type,
        T dataSocialEvent
) {
}
