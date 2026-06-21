package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.social.SocialEventDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void addEmitter(UUID userId, SseEmitter emitter){
        emitters.put(userId,emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));
    }

    public void broadcastToFriend(SocialEventDTO<?> socialEventDTO, UUID userId){
        SseEmitter emitter = emitters.get(userId);

        if(emitter!=null){
            sendEvent(socialEventDTO, emitter);
        }
    }

    public void broadcastToFriends(SocialEventDTO<?> socialEventDTO, List<User> friends){
        for(User friend:friends){
            SseEmitter emitter = emitters.get(friend.getId());

            if(emitter==null){
                continue;
            }

            sendEvent(socialEventDTO, emitter);
        }
    }

    private void sendEvent(SocialEventDTO<?> socialEventDTO, SseEmitter emitter){
        try {
            emitter.send(SseEmitter.event().name(socialEventDTO.type().name()).data(socialEventDTO.dataSocialEvent()));
        } catch (IOException e) {
            emitter.complete();
        }
    }

}
