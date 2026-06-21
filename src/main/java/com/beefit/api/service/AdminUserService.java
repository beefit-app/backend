package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.BannedUserResponseDTO;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.mapper.BanMapper;
import com.beefit.api.repository.UserRepository;
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
    public BannedUserResponseDTO banUserForDays(UUID id, Integer banDays){

        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);

        // Pra garantir que ele não tente somar dias em um valor que era nulo
        LocalDateTime baseDate = (user.getBannedUntil() != null && user.getBannedUntil().isAfter(LocalDateTime.now()))
                ? user.getBannedUntil()
                : LocalDateTime.now();

        user.setBannedUntil(baseDate.plusDays(banDays));

        return banMapper.toResponseDTO(user, banDays);
    }

    @Transactional
    public BannedUserResponseDTO unbanUser(UUID id){

        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);

        user.setBannedUntil(null);

        return banMapper.toResponseDTO(user, 0);
    }
}
