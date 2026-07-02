package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.PublicUserProfileResponseDTO;
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
import jakarta.persistence.EntityManager;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutMediaService workoutMediaService;
    private final FriendshipService friendshipService;
    private final EntityManager entityManager;

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
        User user = findVisibleUserByUsername(username);
        return findUserDetails(user);
    }

    @Transactional(readOnly = true)
    public PublicUserProfileResponseDTO getPublicUserProfile(User viewer, String username) {
        User user = findVisibleUserByUsername(username);
        var relationship = friendshipService.getRelationship(viewer, user);

        return new PublicUserProfileResponseDTO(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getBiography(),
                user.getProfilePicture(),
                user.getBodyWeight(),
                user.getOffensiveHoneys(),
                user.getCardsCount(),
                user.getWorkoutsCount(),
                relationship.status(),
                relationship.friendshipId());
    }

    public java.util.List<java.time.LocalDateTime> getTrainingDays(UUID userId, YearMonth month) {
        return workoutSessionRepository.findFinishedDaysByUserAndMonth(userId, WorkoutStatus.FINISHED,
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    public java.util.List<java.time.LocalDateTime> getPublicTrainingDays(String username, YearMonth month) {
        User user = findVisibleUserByUsername(username);
        return getTrainingDays(user.getId(), month);
    }

    private User findVisibleUserByUsername(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);

        if (user.getGhostMode()) {
            throw new UserNotFoundException();
        }

        return user;
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

        // 1. Apagar arquivos de mídia
        workoutMediaService.deleteAllForOwner(userId);

        // 2. Limpar sessões de autenticação do usuário
        entityManager.createNativeQuery("DELETE FROM auth_sessions WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 3. Remover amizades / convites pendentes do usuário
        entityManager.createNativeQuery("DELETE FROM friendship WHERE requester_id = :userId OR addressee_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 4. Remover curtidas dadas pelo usuário ou nos posts do usuário
        entityManager.createNativeQuery("DELETE FROM post_likes WHERE user_id = :userId OR post_id IN (SELECT id FROM posts WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 5. Apagar postagens do usuário
        entityManager.createNativeQuery("DELETE FROM posts WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 6. Remover inscrições do usuário em colmeias, ou todos os membros de colmeias criadas por ele
        entityManager.createNativeQuery("DELETE FROM hive_members WHERE user_id = :userId OR hive_id IN (SELECT id FROM hives WHERE creator_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 7. Apagar colmeias criadas pelo usuário
        entityManager.createNativeQuery("DELETE FROM hives WHERE creator_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 8. Desassociar templates clonados
        entityManager.createNativeQuery("UPDATE workout_templates SET friend_source_id = null WHERE friend_source_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 9. Apagar exercícios de templates criados pelo usuário
        entityManager.createNativeQuery("DELETE FROM workout_template_exercises WHERE workout_template_id IN (SELECT id FROM workout_templates WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 10. Apagar templates de treinos criados pelo usuário
        entityManager.createNativeQuery("DELETE FROM workout_templates WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 11. Desassociar cartas geradas das sessões de treino para evitar dependência circular
        entityManager.createNativeQuery("UPDATE workout_sessions SET card_id = null WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 12. Apagar cartas no inventário do usuário
        entityManager.createNativeQuery("DELETE FROM card WHERE inventory_id IN (SELECT id FROM inventory WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 13. Apagar inventário do usuário
        entityManager.createNativeQuery("DELETE FROM inventory WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 14. Apagar séries, exercícios e treinos do usuário
        entityManager.createNativeQuery("DELETE FROM sets WHERE exercise_id IN (SELECT id FROM session_exercises WHERE workout_id IN (SELECT id FROM workout_sessions WHERE user_id = :userId))")
                .setParameter("userId", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM session_exercises WHERE workout_id IN (SELECT id FROM workout_sessions WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM workout_sessions WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 15. Por fim, apagar o registro do usuário
        userRepository.delete(user);
    }
}
