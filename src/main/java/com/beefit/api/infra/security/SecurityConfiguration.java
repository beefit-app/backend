package com.beefit.api.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity //Habilitar configuração do Web Security dentro dessa classe para nós conseguirmos mexer nela
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // Desativa o Csrf
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Passa de Statefull par Stateless
                .authorizeHttpRequests(authorize -> authorize
                        //.requestMatchers(HttpMethod.POST, "/dashboard").hasRole("ADMIN")
                        // Rotas exclusivas de admin ou de autenticação devem ser adicionadas aqui
                        .anyRequest().authenticated()
                )
                .build(); // Builda o Security Filter Chain (Cadeia de verificações para a autenticação)
    };
}
