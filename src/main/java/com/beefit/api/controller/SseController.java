package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.exception.user.InvalidSessionException;
import com.beefit.api.infra.security.SecurityFilter;
import com.beefit.api.service.SseService;
import com.beefit.api.service.WorkoutSessionService;
import com.beefit.api.dto.workout.WorkoutPresenceEventDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;
    private final WorkoutSessionService workoutSessionService;

    @GetMapping(value = "/feed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal User user, HttpServletRequest request) {
        if (!(request.getAttribute(SecurityFilter.VERIFIED_SESSION_ATTRIBUTE)
                instanceof SecurityFilter.VerifiedSession session)) {
            throw new InvalidSessionException("Sessão inválida.");
        }
        return sseService.subscribe(user.getId(), session.id(), session.accessExpiresAt(), session.sessionExpiresAt());
    }

    @GetMapping("/live")
    public java.util.List<WorkoutPresenceEventDTO> getLiveWorkouts(@AuthenticationPrincipal User user) {
        return workoutSessionService.getPublicActiveWorkouts(user.getId());
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
