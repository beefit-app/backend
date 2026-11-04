package com.beefit.api.integration;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.SocialProvider;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.UserSocialAccountRepository;
import com.beefit.api.support.OAuth2ProviderStubSupport;
import com.beefit.api.support.PostgreSqlTestSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cobre o fluxo completo de login social (Authorization Code via
 * spring-boot-starter-oauth2-client) de ponta a ponta: redirect de autorização,
 * callback do provedor (simulado via WireMock — token/jwks/userinfo), geração do
 * código de troca, e a troca pela sessão real em /auth/social/exchange.
 *
 * A maior parte dos cenários testa só o registration "google": a lógica de account
 * linking/criação é inteiramente provider-agnostic (SocialAuthService), e a
 * diferença entre Google e Microsoft é só configuração (ver application.yaml). A
 * Apple é mecanicamente diferente o suficiente (callback via POST/form_post,
 * client secret dinâmico, nome só na primeira autorização) pra ter cenários
 * próprios — ver os testes "apple*" abaixo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SocialLoginIntegrationTest {

    private static final String PROVIDER_PATH = "google";
    private static final String CLIENT_ID = "test-google-client-id";
    private static final String ISSUER = "https://accounts.google.com";

    private static final String APPLE_PROVIDER_PATH = "apple";
    private static final String APPLE_CLIENT_ID = "test.apple.services.id";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport.createContainer("social_login_test");

    // Inicializado no carregamento da classe (antes de qualquer callback do JUnit),
    // para que o @DynamicPropertySource abaixo já encontre o servidor no ar.
    static final WireMockServer WIREMOCK = startWireMock();
    static final OAuth2ProviderStubSupport STUBS = new OAuth2ProviderStubSupport(WIREMOCK);

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
        String base = WIREMOCK.baseUrl();
        registry.add("spring.security.oauth2.client.provider.google.token-uri", () -> base + "/google/token");
        registry.add("spring.security.oauth2.client.provider.google.jwk-set-uri", () -> base + "/google/jwks");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri", () -> base + "/google/userinfo");
        // Preserva o "?response_mode=form_post" já embutido na authorization-uri do
        // application-test.yaml — só os outros dois precisam apontar pro WireMock
        // (sem user-info-uri: a Apple não tem endpoint de userinfo).
        registry.add("spring.security.oauth2.client.provider.apple.token-uri", () -> base + "/apple/token");
        registry.add("spring.security.oauth2.client.provider.apple.jwk-set-uri", () -> base + "/apple/jwks");
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired UserSocialAccountRepository socialAccountRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
        STUBS.stubJwksEndpoints(PROVIDER_PATH, APPLE_PROVIDER_PATH);
    }

    @Test
    void newUserSignsUpViaSocialLoginAndReceivesValidJwt() throws Exception {
        String email = "novo." + UUID.randomUUID() + "@gmail.com";
        String providerUserId = "google-sub-" + UUID.randomUUID();

        String exchangeCode = queryParam(runAuthorizationCodeFlow(providerUserId, email, true, "Novo Usuário"), "code");

        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));

        User created = userRepository.findOptionalByEmailIgnoreCase(email).orElseThrow();
        assertNull(created.getPasswordHash());
        assertTrue(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, providerUserId)
                .isPresent());
    }

    @Test
    void existingAccountIsLinkedSilentlyWithoutDuplicating() throws Exception {
        String email = "existente." + UUID.randomUUID() + "@gmail.com";
        User existing = newTraditionalUser(email);
        String providerUserId = "google-sub-" + UUID.randomUUID();

        String exchangeCode = queryParam(runAuthorizationCodeFlow(providerUserId, email, true, "Conta Existente"), "code");

        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));

        assertEquals(1, userRepository.findAll().stream().filter(u -> u.getEmail().equals(email)).count());
        var link = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, providerUserId)
                .orElseThrow();
        assertEquals(existing.getId(), link.getUser().getId());
    }

    /**
     * users.email tem constraint UNIQUE: duas contas não podem ter o mesmo e-mail,
     * verificado ou não. Como não dá pra confiar na posse de um e-mail não
     * verificado, o único resultado seguro é rejeitar o login social (nunca linkar
     * "no escuro" nem tentar criar uma segunda conta colidindo com a existente).
     */
    @Test
    void unverifiedEmailCollidingWithExistingAccountIsRejected() throws Exception {
        String email = "naoverificado." + UUID.randomUUID() + "@gmail.com";
        User existing = newTraditionalUser(email);
        String providerUserId = "google-sub-" + UUID.randomUUID();

        String redirectLocation = runAuthorizationCodeFlow(providerUserId, email, false, "Email Não Verificado");

        assertTrue(redirectLocation.contains("error=social_login_failed"));
        assertTrue(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, providerUserId).isEmpty());
        assertEquals(1, userRepository.findAll().stream().filter(u -> u.getEmail().equals(email)).count());
        assertTrue(userRepository.findById(existing.getId()).isPresent());
    }

    @Test
    void invalidProviderTokenRedirectsWithErrorAndNeverIssuesExchangeCode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult authorizeResult = mvc.perform(get("/oauth2/authorization/google")
                        .session(session).param("platform", "web").param("deviceId", "device-invalid"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String state = queryParam(authorizeResult.getResponse().getHeader("Location"), "state");

        STUBS.stubTokenExchangeFailure(PROVIDER_PATH);

        MvcResult callbackResult = mvc.perform(get("/login/oauth2/code/google")
                        .session(session).param("code", "irrelevant-code").param("state", state))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = callbackResult.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.contains("error=oauth_failed"));
    }

    @Test
    void expiredOrAlreadyUsedExchangeCodeIsRejectedWithUnauthorized() throws Exception {
        String providerUserId = "google-sub-" + UUID.randomUUID();
        String redirectLocation = runAuthorizationCodeFlow(providerUserId, "uso.unico." + UUID.randomUUID() + "@gmail.com",
                true, "Uso Único");
        String exchangeCode = queryParam(redirectLocation, "code");

        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isOk());

        // Segunda troca com o mesmo codigo: ja foi consumido, deve ser rejeitada.
        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_SOCIAL_EXCHANGE_CODE"));

        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"codigo-que-nunca-existiu\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_SOCIAL_EXCHANGE_CODE"));
    }

    @Test
    void appleAuthorizationRequestIncludesFormPostResponseMode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult result = mvc.perform(get("/oauth2/authorization/apple")
                        .session(session).param("platform", "web").param("deviceId", "device-" + UUID.randomUUID()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.contains("response_mode=form_post"),
                "Sem response_mode=form_post a Apple rejeita a autorização inteira quando o scope inclui name/email");
    }

    @Test
    void appleFirstLoginCapturesNameFromUserFormParamAndCreatesAccount() throws Exception {
        String email = "apple.novo." + UUID.randomUUID() + "@icloud.com";
        String providerUserId = "apple-sub-" + UUID.randomUUID();
        String userJson = "{\"name\":{\"firstName\":\"Davi\",\"lastName\":\"Mendes\"}}";

        String exchangeCode = queryParam(runAppleAuthorizationCodeFlow(providerUserId, email, userJson), "code");
        assertNotNull(exchangeCode);

        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"" + exchangeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.fullName").value("Davi Mendes"));

        assertTrue(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, providerUserId)
                .isPresent());
    }

    /**
     * A Apple só manda o parâmetro "user" (com o nome) na primeira autorização
     * daquele usuário com esse client — reautenticações seguintes não mandam mais.
     * Isso não pode duplicar a conta nem apagar o nome já salvo no primeiro login.
     */
    @Test
    void appleSubsequentLoginWithoutUserParamReusesExistingLinkWithoutDuplicating() throws Exception {
        String email = "apple.existente." + UUID.randomUUID() + "@icloud.com";
        String providerUserId = "apple-sub-" + UUID.randomUUID();
        String firstLoginUserJson = "{\"name\":{\"firstName\":\"Davi\",\"lastName\":\"Mendes\"}}";

        String firstCode = queryParam(runAppleAuthorizationCodeFlow(providerUserId, email, firstLoginUserJson), "code");
        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"" + firstCode + "\"}"))
                .andExpect(status().isOk());

        var firstLink = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, providerUserId)
                .orElseThrow();
        UUID userId = firstLink.getUser().getId();

        String secondCode = queryParam(runAppleAuthorizationCodeFlow(providerUserId, email, null), "code");
        mvc.perform(post("/auth/social/exchange").contentType("application/json")
                        .content("{\"code\":\"" + secondCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.fullName").value("Davi Mendes"));

        assertEquals(1, userRepository.findAll().stream().filter(u -> u.getEmail().equals(email)).count());
    }

    /**
     * Executa authorization request -> callback do provedor (stubado) -> devolve a
     * URL de redirect final (contém "?code=..." no sucesso ou "?error=..." na falha).
     */
    private String runAuthorizationCodeFlow(String providerUserId, String email, Boolean emailVerified, String fullName)
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult authorizeResult = mvc.perform(get("/oauth2/authorization/google")
                        .session(session).param("platform", "web").param("deviceId", "device-" + UUID.randomUUID()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorizeResult.getResponse().getHeader("Location");
        String state = queryParam(location, "state");
        String nonce = queryParam(location, "nonce");

        String idToken = STUBS.signIdToken(ISSUER, CLIENT_ID, providerUserId, email, emailVerified, fullName, nonce);
        STUBS.stubTokenExchange(PROVIDER_PATH, idToken);
        STUBS.stubUserInfo(PROVIDER_PATH, providerUserId);

        MvcResult callbackResult = mvc.perform(get("/login/oauth2/code/google")
                        .session(session).param("code", "test-auth-code").param("state", state))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectLocation = callbackResult.getResponse().getHeader("Location");
        assertNotNull(redirectLocation);
        return redirectLocation;
    }

    /**
     * Equivalente a runAuthorizationCodeFlow, mas pro fluxo da Apple: callback via
     * POST (response_mode=form_post), e com o parâmetro "user" opcional (só vem
     * preenchido no primeiro login; passar null simula uma reautenticação).
     */
    private String runAppleAuthorizationCodeFlow(String providerUserId, String email, String userJson) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult authorizeResult = mvc.perform(get("/oauth2/authorization/apple")
                        .session(session).param("platform", "web").param("deviceId", "device-" + UUID.randomUUID()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorizeResult.getResponse().getHeader("Location");
        String state = queryParam(location, "state");
        String nonce = queryParam(location, "nonce");

        // A Apple nunca manda "name" como claim do id_token — isso é testado à parte
        // via o parâmetro de formulário "user" (ver resolveFullName no success handler).
        String idToken = STUBS.signIdToken(APPLE_ISSUER, APPLE_CLIENT_ID, providerUserId, email, true, null, nonce);
        STUBS.stubTokenExchange(APPLE_PROVIDER_PATH, idToken);

        var requestBuilder = post("/login/oauth2/code/apple")
                .session(session).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("code", "test-auth-code").param("state", state);
        if (userJson != null) {
            requestBuilder = requestBuilder.param("user", userJson);
        }

        MvcResult callbackResult = mvc.perform(requestBuilder)
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectLocation = callbackResult.getResponse().getHeader("Location");
        assertNotNull(redirectLocation);
        return redirectLocation;
    }

    private String queryParam(String url, String name) {
        // Os componentes vem crus (ainda percent-encoded) do parser; precisa decodificar
        // manualmente antes de reusar o valor como param de uma nova requisicao mockada
        // (que, diferente de um servidor HTTP real, nao decodifica a query string por nos).
        String rawValue = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return rawValue == null ? null : UriUtils.decode(rawValue, StandardCharsets.UTF_8);
    }

    private User newTraditionalUser(String email) {
        User user = new User();
        user.setFullName("Usuário Tradicional");
        user.setUsername("trad-" + UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("test-password"));
        user.setBodyWeight(75F);
        user.setWeeklyGoal(4);
        user.setRole(UserRole.USER);
        return userRepository.saveAndFlush(user);
    }
}
