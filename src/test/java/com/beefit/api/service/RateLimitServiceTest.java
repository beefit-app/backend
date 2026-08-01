package com.beefit.api.service;

import com.beefit.api.exception.security.RateLimitExceededException;
import com.beefit.api.repository.RateLimitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RateLimitRepository repository;

    @Test
    void rejectsLoginWhenAccountLimitIsExceededWithoutPersistingPlainEmail() {
        when(repository.increment(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(1, 6);
        RateLimitService service = new RateLimitService(repository);

        assertThrows(RateLimitExceededException.class,
                () -> service.checkLogin("192.0.2.1", "Sensitive@Example.com"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2))
                .increment(keys.capture(), any(Instant.class), any(Instant.class));
        assertFalse(keys.getAllValues().stream().anyMatch(key -> key.contains("Sensitive") || key.contains("example.com")));
    }
}
