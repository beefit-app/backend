package com.beefit.api.service;

import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.User;
import com.beefit.api.domain.UserSocialAccount;
import com.beefit.api.domain.enums.SocialProvider;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.exception.user.EmailAlreadyInUse;
import com.beefit.api.exception.user.UnverifiedSocialEmailException;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.UserSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolve a conta local correspondente a um login social, aplicando a regra de
 * account linking: vincula silenciosamente a uma conta existente quando o
 * e-mail do provedor é verificado, ou cria uma conta nova (sem senha local).
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final InventoryRepository inventoryRepository;
    private final RegistrationPolicy registrationPolicy;

    @Transactional
    public User resolveOrCreate(SocialProvider provider, String providerUserId, String email,
            boolean emailVerified, String fullNameClaim) {
        Optional<UserSocialAccount> existingLink =
                userSocialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existingLink.isPresent()) {
            // UserSocialAccount.user é @ManyToOne(LAZY): getUser() devolve um proxy do
            // Hibernate. getId() não dispara select (o FK já é conhecido), mas qualquer
            // outro campo dispararia — e o caller (OAuth2LoginSuccessHandler) usa esse
            // User fora da transação desse service, onde a sessão do Hibernate já
            // fechou. Buscar pelo id devolve a entidade de verdade, já inicializada.
            UUID existingUserId = existingLink.get().getUser().getId();
            return userRepository.findById(existingUserId)
                    .orElseThrow(() -> new IllegalStateException(
                            "user_social_accounts aponta para um usuário inexistente: " + existingUserId));
        }

        String normalizedEmail = normalizeEmail(email);
        Optional<User> existingUserByEmail = userRepository.findOptionalByEmailIgnoreCase(normalizedEmail);

        // So confiamos na posse do e-mail quando o provedor o marca como verificado;
        // sem isso, linkar por e-mail abriria uma forma de assumir a conta de outra pessoa.
        if (emailVerified && existingUserByEmail.isPresent()) {
            linkAccount(existingUserByEmail.get(), provider, providerUserId);
            return existingUserByEmail.get();
        }

        // users.email tem constraint UNIQUE — nao ha como criar uma segunda conta com
        // esse e-mail mesmo que nao verificado. Recusar explicitamente aqui em vez de
        // deixar a inserção falhar com uma violação de constraint crua.
        if (existingUserByEmail.isPresent()) {
            throw new EmailAlreadyInUse(
                    "Esse e-mail já está associado a outra conta. Verifique seu e-mail com o provedor ou entre com e-mail e senha.");
        }

        // Nunca criar conta com e-mail nao verificado: alguem poderia "reservar" o
        // e-mail de outra pessoa, e quando o dono real entrasse depois com um provedor
        // que verifica o e-mail, o login seria vinculado a essa conta pre-criada (que
        // o atacante continua acessando pelo proprio vinculo social).
        if (!emailVerified || normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new UnverifiedSocialEmailException(
                    "O provedor não confirmou a verificação do seu e-mail. Verifique o e-mail na sua conta do provedor e tente novamente.");
        }

        // Mesmas regras do cadastro com e-mail/senha, mas só aqui, na criação: quem
        // já tem conta (vínculo existente ou e-mail verificado acima) continua
        // entrando mesmo com o cadastro fechado ou fora da lista do beta.
        registrationPolicy.ensureRegistrationOpen();
        registrationPolicy.ensureEmailAllowed(normalizedEmail);

        User newUser = createUser(normalizedEmail, fullNameClaim);
        linkAccount(newUser, provider, providerUserId);
        return newUser;
    }

    private User createUser(String email, String fullNameClaim) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(normalizeFullName(fullNameClaim, email));
        user.setUsername(generateUniqueUsername(email));
        // Peso, meta semanal e aceite dos termos ficam de fora de propósito: o provedor
        // não os fornece e inventar valores distorceria calorias/cards (e o aceite
        // precisa ser do usuário). A conta nasce incompleta e o app pede esses dados
        // na tela "Complete seu cadastro" (POST /auth/complete-profile).
        user.setRole(UserRole.USER);

        User savedUser = userRepository.save(user);

        Inventory inventory = new Inventory();
        inventory.setUser(savedUser);
        inventory.setTotalCards(0);
        inventoryRepository.save(inventory);

        return savedUser;
    }

    private void linkAccount(User user, SocialProvider provider, String providerUserId) {
        UserSocialAccount account = new UserSocialAccount();
        account.setUser(user);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        account.setCreatedAt(LocalDateTime.now());
        userSocialAccountRepository.save(account);
    }

    private String normalizeFullName(String fullNameClaim, String email) {
        String candidate = fullNameClaim == null ? "" : fullNameClaim.trim();
        if (candidate.length() >= 4) {
            return candidate.length() > 100 ? candidate.substring(0, 100) : candidate;
        }
        String localPart = localPart(email);
        StringBuilder padded = new StringBuilder(localPart.isBlank() ? "usuario" : localPart);
        while (padded.length() < 4) {
            padded.append(padded);
        }
        return padded.substring(0, Math.min(padded.length(), 100));
    }

    private String generateUniqueUsername(String email) {
        String base = localPart(email).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        int suffix = 0;
        while (userRepository.findByUsernameIgnoreCase(candidate).isPresent()) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }

    private String localPart(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
