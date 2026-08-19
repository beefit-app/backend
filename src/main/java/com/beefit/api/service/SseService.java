package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.social.SocialEventDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SseService {

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void addEmitter(UUID userId, SseEmitter emitter){
        emitters.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError((e) -> removeEmitter(userId, emitter));
    }

    public void broadcastToFriend(SocialEventDTO<?> socialEventDTO, UUID userId){
        afterCommit(() -> dispatchToUser(socialEventDTO, userId));
    }

    private void dispatchToUser(SocialEventDTO<?> socialEventDTO, UUID userId) {
        emitters.getOrDefault(userId, Set.of()).forEach(emitter -> sendEvent(socialEventDTO, emitter));
    }

    public void broadcastToFriends(SocialEventDTO<?> socialEventDTO, List<User> friends){
        List<UUID> friendIds = friends.stream().map(User::getId).toList();
        afterCommit(() -> friendIds.forEach(id -> dispatchToUser(socialEventDTO, id)));
    }

    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> userEmitters.forEach(emitter -> {
            try { emitter.send(SseEmitter.event().name("heartbeat").data("{}")); }
            catch (IOException exception) { removeEmitter(userId, emitter); emitter.complete(); }
        }));
    }

    private void sendEvent(SocialEventDTO<?> socialEventDTO, SseEmitter emitter){
        try {
            emitter.send(SseEmitter.event().name(socialEventDTO.type().name()).data(socialEventDTO.dataSocialEvent()));
        } catch (IOException e) {
            emitter.complete();
        }
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters);
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

}
