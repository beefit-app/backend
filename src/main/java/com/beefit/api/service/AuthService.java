package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.LoginResponseDTO;
import com.beefit.api.dto.UserResponseDTO;
import com.beefit.api.exception.EmailAlreadyInUse;
import com.beefit.api.exception.UsernameAlreadyInUse;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserResponseMapper userResponseMapper;
    private final TokenService tokenService;
    private final AuthenticationConfiguration authenticationConfiguration;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserDetails user = userRepository.findByEmail(email);

        if(user == null){
            throw new UsernameNotFoundException("Usuário não encontrado");
        }

        return user;
    }

    public LoginResponseDTO login(String email, String password) {

        var usernamePassword = new UsernamePasswordAuthenticationToken(email, password);

        var authenticationManager = authenticationConfiguration.getAuthenticationManager();
        var auth = authenticationManager.authenticate(usernamePassword);

        var token = tokenService.generateToken((User)auth.getPrincipal());

        UserResponseDTO login = userResponseMapper.toDto((User)auth.getPrincipal());

        return new LoginResponseDTO(token, login);
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
