package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.ChangePasswordDTO;
import com.beefit.api.dto.DeleteUserRequestDTO;
import com.beefit.api.dto.UpdateAccountSettingsRequestDTO;
import com.beefit.api.dto.UpdateProfileRequestDTO;
import com.beefit.api.dto.UserProfileResponseDTO;
import com.beefit.api.exception.InvalidPasswordException;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Logica para Visualizar o Perfil (RF-003)
    public UserProfileResponseDTO getUserProfile(UUID userId) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Atributos simulados enquanto os outros módulos da equipe não estão prontos
            Integer mockCardsCount = 0;
            Integer mockTotalWorkouts = 0;

            // Retorna a estrutura com dados do banco e os contadores
            return new UserProfileResponseDTO(
                    user.getFullName(),
                    user.getUsername(),
                    user.getBiography(),
                    user.getProfilePicture(),
                    user.getBodyWeight(),
                    user.getOffensiveHoneys(),
                    mockCardsCount,
                    mockTotalWorkouts,
                    user.getGhostMode());
        } else {
            throw new UserNotFoundException();
        }
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
            user.setBodyWeight(request.bodyWeight());
            // O Spring Data JPA salva automaticamente no final do metodo devido ao
            // @Transactional
        } else {
            throw new UserNotFoundException();
        }
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

        userRepository.delete(user);
    }
}
