package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.dto.SocialEventDTO;
import com.beefit.api.dto.WorkoutPresenceEventDTO;
import com.beefit.api.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/feed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal User user){
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        sseService.addEmitter(user.getId(), emitter);

        return emitter;
    }

//    @PostMapping("/test")
//    public void test() {
//
//        WorkoutPresenceEventDTO presenceEvent = new WorkoutPresenceEventDTO(
//                SocialEvent.WORKOUT_STARTED.name(),
//                UUID.randomUUID(),
//                UUID.randomUUID(),
//                "Murilo",
//                "Murilo",
//                "IN_PROGRESS",
//                LocalDateTime.now(),
//                List.of("peito"));
//
//        sseService.broadcast(
//                new SocialEventDTO(
//                        SocialEvent.WORKOUT_STARTED,
//                        presenceEvent
//                )
//        );
//    }
}
