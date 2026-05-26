package com.beefit.api.service;

import com.beefit.api.dto.SocialEventDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void addEmitter(SseEmitter emitter){
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
    }

    public void broadcast(SocialEventDTO socialEventDTO){
        List<SseEmitter> closedEmitters = new CopyOnWriteArrayList<>();

        for(SseEmitter emitter:emitters){
            try {
                emitter.send(SseEmitter.event().name(socialEventDTO.type().name()).data(socialEventDTO.dataSocialEvent()));
            } catch (IOException e) {
                closedEmitters.add(emitter);
            }
        }
        emitters.removeAll(closedEmitters);
    }
}
