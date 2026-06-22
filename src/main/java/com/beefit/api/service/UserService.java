package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.user.UpdateProfileRequestDTO;
import com.beefit.api.dto.user.UserProfileResponseDTO;
import com.beefit.api.exception.user.InvalidPasswordException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.time.YearMonth;
import java.time.LocalDate;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.domain.enums.WorkoutStatus;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutMediaService workoutMediaService;

    private UserProfileResponseDTO findUserDetails(User user){

        // Retorna a estrutura com dados do banco e os contadores
        return new UserProfileResponseDTO(
                user.getFullName(),
                user.getUsername(),
                user.getBiography(),
                user.getProfilePicture(),
                user.getBodyWeight(),
                user.getOffensiveHoneys(),
                user.getCardsCount(),
                user.getWorkoutsCount(),
                user.getGhostMode());
    }

    // Logica para Visualizar o próprio Perfil (RF-003)
    public UserProfileResponseDTO getUserProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        return findUserDetails(user);
    }

    // Visualizar perfil de outros
    public UserProfileResponseDTO getUserProfileByUsername(String username){
        User user = userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);

        if(user.getGhostMode()){
            throw new UserNotFoundException();
        }

        return findUserDetails(user);
    }

    public java.util.List<LocalDate> getTrainingDays(UUID userId, YearMonth month) {
        return workoutSessionRepository.findFinishedDaysByUserAndMonth(userId, WorkoutStatus.FINISHED,
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    // Lógica para Editar o Perfil (RF-003)
    @Transactional
    public void updateUserProfile(UUID userId, UpdateProfileRequestDTO request) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Altera os dados permitidos com base no DTO recebido
            user.setFullName(request.fullName());
            user.setBiography(request.biography());
            user.setProfilePicture(request.profilePicture());
            consumeProfileMedia(request.profilePicture(), user.getId());
            user.setBodyWeight(request.bodyWeight());
            // O Spring Data JPA salva automaticamente no final do metodo devido ao
            // @Transactional
        } else {
            throw new UserNotFoundException();
        }
    }

    private void consumeProfileMedia(String value, UUID userId) {
        if (value == null || !value.startsWith("/api/media/")) return;
        try { workoutMediaService.consumeForProfile(UUID.fromString(value.substring(value.lastIndexOf('/') + 1)), userId); }
        catch (IllegalArgumentException ignored) { /* URLs externas permanecem compatíveis */ }
    }

    @Transactional
    public void changeUserPassword(UUID userId, ChangePasswordDTO dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getPassword() == null || !passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Senha atual incorreta.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
    }

    @Transactional
    public void updateUserGhostMode(UUID userId, UpdateAccountSettingsRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        user.setGhostMode(request.ghostMode());
    }

    @Transactional
    public void deleteAccount(UUID userId, DeleteUserRequestDTO dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getPassword() != null && !passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new InvalidPasswordException("Senha incorreta.");
        }

        workoutMediaService.deleteAllForOwner(userId);
        userRepository.delete(user);
    }
}
