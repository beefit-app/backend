package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.RegisterDTO;
import com.beefit.api.exception.EmailAlreadyInUse;
import com.beefit.api.exception.UsernameAlreadyInUse;
import com.beefit.api.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserDetails user = userRepository.findByEmail(email);

        if(user == null){
            throw new UsernameNotFoundException("Usuário não encontrado");
        }

        return user;
    }

    public void register(User registered) throws UsernameAlreadyInUse, EmailAlreadyInUse {

        if(userRepository.findByUsername(registered.getUsername()) != null){
            throw new UsernameAlreadyInUse("Esse nome de usuário já está em uso.");
        };

        if(userRepository.findByEmail(registered.getEmail()) != null){
            throw new EmailAlreadyInUse("Esse email já está em uso.");
        };

        registered.setPasswordHash(passwordEncoder.encode(registered.getPasswordHash()));
        registered.setRole(UserRole.USER);

        userRepository.save(registered);
    }

}
