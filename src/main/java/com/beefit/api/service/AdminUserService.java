package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.BannedUserResponseDTO;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.exception.user.PermissionException;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.mapper.BanMapper;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.utils.log.LogAudit;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final BanMapper banMapper;

    public Page<User> searchUsers(String search, Pageable pageable){

        return userRepository.getAllBySearchAndPagination(search, pageable);
    }

    public User getUser(UUID id){

        return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    @LogAudit(action = "BAN_USER", description = "Banimento de usuário por violação de termos.")
    public BannedUserResponseDTO banUserForDays(UUID actingAdminId, UUID id, Integer banDays){

        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        if (actingAdminId.equals(id) || user.getRole() == UserRole.ADMIN) {
            throw new PermissionException("Contas administrativas não podem ser banidas por esta operação.");
        }
        int days = banDays == null || banDays < 1 ? 7 : banDays;

        // Pra garantir que ele não tente somar dias em um valor que era nulo
        LocalDateTime baseDate = (user.getBannedUntil() != null && user.getBannedUntil().isAfter(LocalDateTime.now()))
                ? user.getBannedUntil()
                : LocalDateTime.now();

        user.setBannedUntil(baseDate.plusDays(days));

        return banMapper.toResponseDTO(user, days);
    }

    @Transactional
    @LogAudit(action = "UNBAN_USER", description = "Finaliza período de banimento do usuário.")
    public BannedUserResponseDTO unbanUser(UUID id){

        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);

        user.setBannedUntil(null);

        return banMapper.toResponseDTO(user, 0);
    }
}
