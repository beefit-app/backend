package com.beefit.api.service;


import com.beefit.api.domain.User;
import com.beefit.api.dto.user.ChangePasswordDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.dto.user.PublicUserProfileResponseDTO;
import com.beefit.api.dto.user.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.user.UpdateProfileRequestDTO;
import com.beefit.api.dto.user.UserProfileResponseDTO;
import com.beefit.api.dto.social.ProfileUpdatedEventDTO;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.exception.user.InvalidPasswordException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.time.YearMonth;
import java.time.LocalDate;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.domain.enums.WorkoutStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MIN_VALID_WORKOUT_DURATION_SECONDS = 1800;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutMediaService workoutMediaService;
    private final FriendshipService friendshipService;
    private final SseService sseService;
    private final EntityManager entityManager;
    private final AuthSessionRepository authSessionRepository;

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
                Math.toIntExact(workoutSessionRepository.countValidFinishedWorkoutsByUserId(
                        user.getId(), WorkoutStatus.FINISHED, MIN_VALID_WORKOUT_DURATION_SECONDS)),
                user.getGhostMode(),
                user.hasDeveloperBadge());
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
                user.getOffensiveHoneys(),
                user.getCardsCount(),
                Math.toIntExact(workoutSessionRepository.countValidFinishedWorkoutsByUserId(
                        user.getId(), WorkoutStatus.FINISHED, MIN_VALID_WORKOUT_DURATION_SECONDS)),
                relationship.status(),
                relationship.friendshipId(),
                user.hasDeveloperBadge());
    }

    public java.util.List<java.time.LocalDateTime> getTrainingDays(UUID userId, YearMonth month) {
        return workoutSessionRepository.findFinishedDaysByUserAndMonth(userId, WorkoutStatus.FINISHED,
                MIN_VALID_WORKOUT_DURATION_SECONDS,
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    public java.util.List<java.time.LocalDateTime> getPublicTrainingDays(User viewer, String username, YearMonth month) {
        User user = findVisibleUserByUsername(username);
        if (!viewer.getId().equals(user.getId())
                && !"FRIEND".equals(friendshipService.getRelationship(viewer, user).status())) {
            // Mantém a mesma semântica dos posts: não revelar se o alvo existe
            // nem seu histórico de atividade fora de uma amizade aceita.
            throw new UserNotFoundException();
        }
        return getTrainingDays(user.getId(), month);
    }

    private User findVisibleUserByUsername(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username.trim()).orElseThrow(UserNotFoundException::new);

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
            String previousProfilePicture = user.getProfilePicture();
            consumeProfileMedia(request.profilePicture(), user.getId());
            user.setProfilePicture(request.profilePicture());
            workoutMediaService.releasePreviousProfileMedia(previousProfilePicture, request.profilePicture(), user.getId());
            user.setBodyWeight(request.bodyWeight());
            broadcastProfileUpdated(user);
            // O Spring Data JPA salva automaticamente no final do metodo devido ao
            // @Transactional
        } else {
            throw new UserNotFoundException();
        }
    }

    private void broadcastProfileUpdated(User user) {
        var recipients = new java.util.ArrayList<>(friendshipService.getFriends(user.getId()).orElseGet(java.util.List::of));
        recipients.add(user);
        var event = new ProfileUpdatedEventDTO(user.getId(), user.getFullName(), user.getUsername(), user.getProfilePicture());
        sseService.broadcastToFriends(new SocialEventDTO<>(SocialEvent.PROFILE_UPDATED, event), recipients.stream().distinct().toList());
    }

    private void consumeProfileMedia(String value, UUID userId) {
        if (value == null || !value.startsWith("/api/media/")) return;
        try {
            workoutMediaService.consumeForProfile(UUID.fromString(value.substring(value.lastIndexOf('/') + 1)), userId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Foto de perfil inválida.");
        }
    }

    @Transactional
    public void changeUserPassword(UUID userId, ChangePasswordDTO dto) {
        User user = findUserForUpdate(userId);

        if (user.getPassword() == null || !passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Senha atual incorreta.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));

        // Quem troca a senha normalmente esta reagindo a uma suspeita de invasao;
        // sem isto o invasor seguiria dentro com o refresh token antigo, que vale
        // 30 dias e se renova. Cai tambem o aparelho que fez a troca, que precisa
        // entrar de novo: poupar a sessao atual exigiria carregar o id dela ate
        // aqui, e o ganho nao paga a complexidade.
        authSessionRepository.revokeAllForOwner(userId, LocalDateTime.now());
    }

    private User findUserForUpdate(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        try {
            // Trava a mesma linha usada por AuthService. O refresh da entidade
            // descarta uma leitura anterior ao lock, inclusive com open-in-view.
            entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException removedDuringLock) {
            throw new UserNotFoundException();
        }
        return user;
    }

    @Transactional
    public void updateUserGhostMode(UUID userId, UpdateAccountSettingsRequestDTO request) {
        User user = findUserForUpdate(userId);
        if (Objects.equals(user.getGhostMode(), request.ghostMode())) return;

        user.setGhostMode(request.ghostMode());
        List<User> friends = friendshipService.getFriends(userId).orElseGet(List::of);
        sseService.broadcastToFriends(new SocialEventDTO<>(SocialEvent.SOCIAL_VISIBILITY_CHANGED, Map.of()), friends);
    }

    @Transactional
    public void deleteAccount(UUID userId, DeleteUserRequestDTO dto) {
        User user = findUserForUpdate(userId);

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

        // 4. Remover denúncias feitas pelo usuário, contra ele, ou contra posts dele.
        // Precisa vir antes da remoção de posts e do próprio usuário: reports tem FK
        // para users e posts, e o delete abaixo é físico.
        entityManager.createNativeQuery("DELETE FROM reports WHERE reporter_id = :userId OR target_user_id = :userId OR target_post_id IN (SELECT id FROM posts WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 5. Remover curtidas dadas pelo usuário ou nos posts do usuário
        entityManager.createNativeQuery("DELETE FROM post_likes WHERE user_id = :userId OR post_id IN (SELECT id FROM posts WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // Comentários usam ON DELETE CASCADE para usuário/post, inclusive denúncias
        // contra esses comentários. Isso também cobre DELETEs nativos neste fluxo.
        // 6. Apagar postagens do usuário
        entityManager.createNativeQuery("DELETE FROM posts WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 7. Remover inscrições do usuário em colmeias, ou todos os membros de colmeias criadas por ele
        entityManager.createNativeQuery("DELETE FROM hive_members WHERE user_id = :userId OR hive_id IN (SELECT id FROM hives WHERE creator_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 8. Apagar colmeias criadas pelo usuário
        entityManager.createNativeQuery("DELETE FROM hives WHERE creator_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 9. Desassociar templates clonados
        entityManager.createNativeQuery("UPDATE workout_templates SET friend_source_id = null WHERE friend_source_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 10. Apagar exercícios de templates criados pelo usuário
        entityManager.createNativeQuery("DELETE FROM workout_template_exercises WHERE workout_template_id IN (SELECT id FROM workout_templates WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 11. Apagar templates de treinos criados pelo usuário
        entityManager.createNativeQuery("DELETE FROM workout_templates WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 12. Desassociar cartas geradas das sessões de treino para evitar dependência circular
        entityManager.createNativeQuery("UPDATE workout_sessions SET card_id = null WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 13. Apagar cartas no inventário do usuário
        entityManager.createNativeQuery("DELETE FROM card WHERE inventory_id IN (SELECT id FROM inventory WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        // 14. Apagar inventário do usuário
        entityManager.createNativeQuery("DELETE FROM inventory WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 15. Apagar séries, exercícios e treinos do usuário
        entityManager.createNativeQuery("DELETE FROM sets WHERE exercise_id IN (SELECT id FROM session_exercises WHERE workout_id IN (SELECT id FROM workout_sessions WHERE user_id = :userId))")
                .setParameter("userId", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM session_exercises WHERE workout_id IN (SELECT id FROM workout_sessions WHERE user_id = :userId)")
                .setParameter("userId", userId).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM workout_sessions WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();

        // 16. Por fim, apagar o registro do usuário
        userRepository.delete(user);
    }
}
