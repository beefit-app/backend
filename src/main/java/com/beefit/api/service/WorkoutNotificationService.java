package com.beefit.api.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.workout.WorkoutPresenceEventDTO;
import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.exception.workout.WorkoutSessionNotFoundException;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutNotificationService {

    private final UserRepository userRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final FriendshipService friendshipService;
    private final SseService sseService;
    private final WorkoutMuscleGroupMapper workoutMuscleGroupMapper;

    public void processWorkoutPresenceNotification(WorkoutFinishedEvent event) {
        if (event.isPrivate()) {
            return;
        }

        User user = userRepository.findById(event.userId())
                .orElseThrow(UserNotFoundException::new);

        if (Boolean.TRUE.equals(user.getGhostMode())) {
            return;
        }

        WorkoutSession session = workoutSessionRepository.findById(event.workoutSessionId())
                .orElseThrow(WorkoutSessionNotFoundException::new);

        WorkoutPresenceEventDTO presenceEvent = new WorkoutPresenceEventDTO(
                SocialEvent.WORKOUT_FINISHED.name(),
                session.getId(),
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getProfilePicture(),
                session.getStatus().getStatus(),
                event.finishedAt(),
                workoutMuscleGroupMapper.toApi(session.getMuscleGroupsList()));

        List<User> friends = friendshipService.getFriends(user.getId()).orElse(List.of());
        sseService.broadcastToFriends(new SocialEventDTO<>(SocialEvent.WORKOUT_FINISHED, presenceEvent), friends);
    }
}
