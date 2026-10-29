package com.beefit.api.infra.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity // Habilitar configuração do Web Security dentro dessa classe para nós
                   // conseguirmos mexer nela
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    @Value("${beefit.security.cors.allowed-origins}")
    private String[] allowedOrigins;

    private final SecurityFilter securityFilter;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable()) // Desativa o Csrf
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Passa
                                                                                                              // de
                                                                                                              // Statefull
                                                                                                              // par
                                                                                                              // Stateless
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            String authFailure = (String) request.getAttribute(SecurityFilter.AUTH_FAILURE_REASON_ATTRIBUTE);
                            String errorCode = "AUTH_REQUIRED";
                            if ("EXPIRED".equals(authFailure)) {
                                errorCode = "TOKEN_EXPIRED";
                            } else if ("INVALID".equals(authFailure)) {
                                errorCode = "TOKEN_INVALID";
                            } else if ("BANNED".equals(authFailure)) {
                                errorCode = "ACCOUNT_BANNED";
                            }
                            String message = switch (errorCode) {
                                case "TOKEN_EXPIRED" -> "Sua sessão expirou.";
                                case "TOKEN_INVALID" -> "Sua sessão é inválida.";
                                case "ACCOUNT_BANNED" -> "Esta conta está temporariamente bloqueada.";
                                default -> "Autenticação obrigatória.";
                            };

                            response.setStatus("ACCOUNT_BANNED".equals(errorCode)
                                    ? HttpServletResponse.SC_FORBIDDEN
                                    : HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            response.getWriter().write("""
                                      {
                                          "status": %d,
                                          "error": "%s",
                                          "message": "%s"
                                      }
                                    """.formatted(response.getStatus(), errorCode, message));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("""
                                      {
                                          "status": 403,
                                          "error": "ACCESS_DENIED",
                                          "message": "Você não tem permissão para executar esta operação."
                                      }
                                    """);
                        }))

                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)).permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")))

                .authorizeHttpRequests(authorize -> {
                        // .requestMatchers(HttpMethod.POST, "/dashboard").hasRole("ADMIN")
                        // Rotas exclusivas de admin ou de autenticação devem ser adicionadas aqui
                        authorize
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/legal-versions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN");

                        // O schema OpenAPI alimenta a geracao dos tipos do frontend, que
                        // transforma divergencia de contrato em erro de compilacao. Fica
                        // fora de producao por dois motivos independentes: esta guarda e o
                        // springdoc.api-docs.enabled=false do perfil prod.
                        if (!isProduction()) {
                            authorize.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll();
                        }

                        authorize.anyRequest().authenticated();
                })
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build(); // Builda o Security Filter Chain (Cadeia de verificações para a autenticação)
    };

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = List.of(allowedOrigins);
        if (isProduction()) {
            if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.isBlank() || origin.contains("*"))) {
                throw new IllegalStateException("CORS de produção exige origens exatas, sem curingas.");
            }
            configuration.setAllowedOrigins(origins);
        } else {
            configuration.setAllowedOriginPatterns(origins);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Request-ID"));
        configuration.setExposedHeaders(List.of("Retry-After", "X-Request-ID"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    @Bean
    // Injeção de dependência por parâmetro de método: Spring instância o authenticationConfiguration para passar como parâmetro
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
