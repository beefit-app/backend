package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.exception.user.InvalidSessionException;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.AuthSessionRepository.StreamSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseService {

    private static final int VALIDATION_BATCH_SIZE = 512;

    private final AuthSessionRepository authSessionRepository;
    private final Map<UUID, Set<Subscription>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId, UUID sessionId, Instant accessExpiresAt, LocalDateTime sessionExpiresAt) {
        long timeoutMillis = Math.min(Duration.between(Instant.now(), accessExpiresAt).toMillis(),
                Duration.between(LocalDateTime.now(), sessionExpiresAt).toMillis());
        if (timeoutMillis <= 0) {
            throw new InvalidSessionException("Sessão expirada. Faça login novamente.");
        }

        SseEmitter emitter = new SseEmitter(timeoutMillis);
        var subscription = new Subscription(userId, sessionId, accessExpiresAt, sessionExpiresAt, emitter);
        emitter.onCompletion(() -> remove(subscription));
        emitter.onTimeout(() -> close(subscription));
        emitter.onError(error -> remove(subscription));

        emitters.compute(userId, (id, subscriptions) -> {
            if (subscriptions == null) subscriptions = ConcurrentHashMap.newKeySet();
            subscriptions.add(subscription);
            return subscriptions;
        });
        return emitter;
    }

    public void broadcastToFriend(SocialEventDTO<?> event, UUID userId) {
        broadcastToUsers(event, Set.of(userId));
    }

    public void broadcastToFriends(SocialEventDTO<?> event, List<User> friends) {
        Set<UUID> friendIds = friends.stream().map(User::getId).collect(Collectors.toUnmodifiableSet());
        broadcastToUsers(event, friendIds);
    }

    public void broadcastToUsers(SocialEventDTO<?> event, Collection<UUID> userIds) {
        Set<UUID> recipients = Set.copyOf(userIds);
        if (!recipients.isEmpty()) {
            afterCommit(() -> dispatchToUsers(event, recipients));
        }
    }

    private void dispatchToUsers(SocialEventDTO<?> event, Set<UUID> userIds) {
        var subscriptions = userIds.stream()
                .flatMap(id -> emitters.getOrDefault(id, Set.of()).stream()).toList();
        dispatch(subscriptions, event.type().name(), event.dataSocialEvent());
    }

    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        dispatch(emitters.values().stream().flatMap(Collection::stream).toList(), "heartbeat", "{}");
    }

    private void dispatch(List<Subscription> subscriptions, String event, Object data) {
        for (int start = 0; start < subscriptions.size(); start += VALIDATION_BATCH_SIZE) {
            dispatchBatch(subscriptions.subList(start, Math.min(start + VALIDATION_BATCH_SIZE, subscriptions.size())),
                    event, data);
        }
    }

    private void dispatchBatch(List<Subscription> subscriptions, String event, Object data) {
        // Obtém o estado do banco para este lote, sem confiar no estado da
        // abertura do stream e sem manter locks durante a escrita na rede.
        Map<UUID, StreamSession> active;
        try {
            Set<UUID> ids = subscriptions.stream().map(Subscription::sessionId).collect(Collectors.toSet());
            active = authSessionRepository.findActiveStreamSessions(ids, LocalDateTime.now()).stream()
                    .collect(Collectors.toMap(StreamSession::getId, Function.identity()));
        } catch (DataAccessException | TransactionException failure) {
            log.warn("Falha ao revalidar sessões SSE; encerrando o lote ({})", failure.getClass().getSimpleName());
            subscriptions.forEach(this::close);
            return;
        }

        for (var subscription : subscriptions) {
            var session = active.get(subscription.sessionId());
            LocalDateTime now = LocalDateTime.now();
            if (session == null || !session.getUserId().equals(subscription.userId())
                    || !subscription.accessExpiresAt().isAfter(Instant.now())
                    || !subscription.sessionExpiresAt().isAfter(now) || !session.getExpiresAt().isAfter(now)) {
                close(subscription);
                continue;
            }
            try {
                subscription.emitter().send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException disconnected) {
                close(subscription);
            }
        }
    }

    private void remove(Subscription subscription) {
        emitters.computeIfPresent(subscription.userId(), (id, subscriptions) -> {
            subscriptions.remove(subscription);
            return subscriptions.isEmpty() ? null : subscriptions;
        });
    }

    private void close(Subscription subscription) {
        remove(subscription);
        subscription.emitter().complete();
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record Subscription(UUID userId, UUID sessionId, Instant accessExpiresAt, LocalDateTime sessionExpiresAt,
            SseEmitter emitter) {}
}
