package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.ChangePasswordDTO;
import com.beefit.api.dto.DeleteUserRequest;
import com.beefit.api.dto.UpdateProfileRequest;
import com.beefit.api.dto.UserProfileResponse;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private static UserRepository userRepository;

    // Logica para Visualizar o Perfil (RF-003)
    public UserProfileResponse getUserProfile(UUID userId) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Atributos simulados enquanto os outros módulos da equipe não estão prontos
            Integer mockCardsCount = 0;
            Integer mockTotalWorkouts = 0;

            // Retorna a estrutura com dados do banco e os contadores
            return new UserProfileResponse(
                    user.getFullName(),
                    user.getUsername(),
                    user.getBiography(),
                    user.getProfilePicture(),
                    user.getLevel(),
                    user.getOffensiveHoneys(),
                    mockCardsCount,
                    mockTotalWorkouts
            );
        } else {
            throw new UserNotFoundException();
        }
    }

    // Lógica para Editar o Perfil (RF-003)
    @Transactional
    public void updateUserProfile(UUID userId, UpdateProfileRequest request) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Altera os dados permitidos com base no DTO recebido
            user.setFullName(request.fullName());
            user.setBiography(request.biography());
            user.setProfilePicture(request.profilePicture());
            user.setBodyWeight(request.bodyWeight());
            // O Spring Data JPA salva automaticamente no final do metodo devido ao @Transactional
        } else {
            throw new UserNotFoundException();
        }
    }
    //Method created to change the account password(RF - 004)
    @Transactional
    public static void changeUserPassword(UUID userID, ChangePasswordDTO dto) {
        Optional<User> userOptional = userRepository.findById(userID);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            user.setPasswordHash(dto.newPassword());
        } else {
            throw new UserNotFoundException();
        }
    }

    //delete account (RF - 004)
    @Transactional
    public void deleteAccount(UUID userId, DeleteUserRequest dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        if (user.getPassword() != null && !user.getPassword().matches(dto.password())) {
            throw new IllegalArgumentException("Senha incorreta. Operação cancelada.");
        }

        userRepository.delete(user);
    }
}