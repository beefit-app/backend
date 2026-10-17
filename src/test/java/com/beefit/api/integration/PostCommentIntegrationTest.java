package com.beefit.api.integration;

import com.beefit.api.domain.*;
import com.beefit.api.domain.enums.*;
import com.beefit.api.dto.comment.CreateCommentDTO;
import com.beefit.api.dto.report.CreateReportDTO;
import com.beefit.api.dto.report.ResolveReportDTO;
import com.beefit.api.dto.user.DeleteUserRequestDTO;
import com.beefit.api.repository.*;
import com.beefit.api.service.*;
import com.beefit.api.support.PostgreSqlTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PostCommentIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport.createContainer("comments_test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired WorkoutSessionRepository workouts;
    @Autowired PostRepository posts;
    @Autowired PostCommentRepository comments;
    @Autowired ReportRepository reports;
    @Autowired PostCommentService service;
    @Autowired ReportService reportService;
    @Autowired ReportModerationService moderation;
    @Autowired WorkoutSessionService workoutService;
    @Autowired UserService userService;
    @Autowired TokenService tokenService;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @MockitoBean SseService sse;
    User owner;
    User friend;
    Post post;

    @BeforeEach void setup() {
        owner = newUser();
        friend = newUser();
        var workout = new WorkoutSession();
        workout.setUser(owner);
        workout.setStartedAt(LocalDateTime.now().minusMinutes(40));
        workout.setFinishedAt(LocalDateTime.now());
        workout.setDurationSeconds(2400);
        workout.setStatus(WorkoutStatus.FINISHED);
        workout.setIsPrivate(false);
        workout.setShouldPostWorkout(true);
        workout.setMuscleGroupsList(List.of(MuscleGroups.CHEST));
        workout = workouts.saveAndFlush(workout);
        post = new Post();
        post.setUser(owner);
        post.setWorkoutSession(workout);
        post = posts.saveAndFlush(post);
    }

    @Test void validatesHttpLimitsAuthAndStablePagination() throws Exception {
        String endpoint = "/api/posts/" + post.getId() + "/comments";
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint).with(user(owner)).contentType("application/json").content("{\"text\":\"" + "a".repeat(360) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canDelete").value(true));
        mvc.perform(post(endpoint).with(user(owner)).contentType("application/json").content("{\"text\":\"" + "a".repeat(361) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).with(user(owner)).contentType("application/json").content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(endpoint).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(endpoint).with(user(friend))).andExpect(status().isNotFound());
        mvc.perform(post(endpoint).with(user(friend)).contentType("application/json").content("{\"text\":\"Sem acesso\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(endpoint + "/count").with(user(friend))).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/feed").with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].commentsCount").value(1));
        mvc.perform(get(endpoint + "?size=51").with(user(owner))).andExpect(status().isBadRequest());
    }

    @Test void moderationRemovesCommentAndClosesItsReports() {
        // O dono pode denunciar um comentário recebido; a FK e o CHECK são reais.
        var comment = savedComment(friend);
        var report = reportService.createReport(owner, new CreateReportDTO(ReportTargetType.COMMENT, comment.getId(), ReportReason.SPAM, null));
        var result = moderation.resolveReport(owner.getId(), report.id(), new ResolveReportDTO(ReportResolutionAction.REMOVE_COMMENT, null));
        assertEquals(1, result.closedReports());
        assertEquals(0, service.count(owner, post.getId()));
        assertTrue(comments.findById(comment.getId()).orElseThrow().isRemoved());
    }

    @Test void deletingWorkoutCascadesCommentsAndTheirReports() {
        var comment = savedComment(friend);
        var report = reportService.createReport(owner, new CreateReportDTO(ReportTargetType.COMMENT, comment.getId(), ReportReason.SPAM, null));
        workoutService.deleteWorkout(owner.getId(), post.getWorkoutSession().getId());
        assertTrue(comments.findById(comment.getId()).isEmpty());
        assertTrue(reports.findById(report.id()).isEmpty());
    }

    @Test void deletingCommentAuthorCascadesCommentsAndTheirReports() {
        var comment = savedComment(friend);
        var report = reportService.createReport(owner, new CreateReportDTO(ReportTargetType.COMMENT, comment.getId(), ReportReason.SPAM, null));
        userService.deleteAccount(friend.getId(), new DeleteUserRequestDTO("test-password"));
        assertTrue(comments.findById(comment.getId()).isEmpty());
        assertTrue(reports.findById(report.id()).isEmpty());
        assertEquals(0, service.count(owner, post.getId()));
    }

    @Test void deletingPostOwnerAccountCascadesReceivedCommentsAndTheirReports() {
        var comment = savedComment(friend);
        var report = reportService.createReport(owner, new CreateReportDTO(ReportTargetType.COMMENT, comment.getId(), ReportReason.SPAM, null));
        userService.deleteAccount(owner.getId(), new DeleteUserRequestDTO("test-password"));
        assertTrue(comments.findById(comment.getId()).isEmpty());
        assertTrue(reports.findById(report.id()).isEmpty());
    }

    @Test void authorCanDeleteWithoutFriendshipButCannotReadThePost() throws Exception {
        var comment = savedComment(friend);
        String endpoint = "/api/posts/" + post.getId() + "/comments";
        mvc.perform(get(endpoint).with(user(friend))).andExpect(status().isNotFound());
        mvc.perform(delete(endpoint + "/" + comment.getId()).with(user(friend))).andExpect(status().isNoContent());
        assertEquals(0, service.count(owner, post.getId()));
    }

    @Test void commentCannotBeDeletedThroughADifferentPost() throws Exception {
        var comment = savedComment(owner);
        mvc.perform(delete("/api/posts/" + UUID.randomUUID() + "/comments/" + comment.getId()).with(user(owner)))
                .andExpect(status().isNotFound());
        assertFalse(comments.findById(comment.getId()).orElseThrow().isRemoved());
    }

    @Test void rejectsBannedPrincipalUsingRealJwt() throws Exception {
        owner.setBannedUntil(LocalDateTime.now().plusDays(1));
        owner = users.saveAndFlush(owner);
        String token = tokenService.generateToken(owner, UUID.randomUUID());
        mvc.perform(get("/api/posts/" + post.getId() + "/comments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test void concurrentCreatesDoNotLoseCommentsOrCounts() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(owner, post.getId(), new CreateCommentDTO("Primeiro")));
            var second = executor.submit(() -> service.create(owner, post.getId(), new CreateCommentDTO("Segundo")));
            assertNotEquals(first.get().id(), second.get().id());
        }
        assertEquals(2, service.count(owner, post.getId()));
        var counts = comments.countByPosts(List.of(post.getId()));
        assertEquals(2, counts.getFirst().getTotal());
        var firstPage = service.list(owner, post.getId(), 0, 1);
        var secondPage = service.list(owner, post.getId(), 1, 1);
        assertNotEquals(firstPage.getContent().getFirst().id(), secondPage.getContent().getFirst().id());
    }

    @Test void removedPrivateAndGhostPostsCannotBeReadByFriends() throws Exception {
        var friendship = new Friendship();
        friendship.setRequester(owner);
        friendship.setAddressee(friend);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendships.saveAndFlush(friendship);
        String endpoint = "/api/posts/" + post.getId() + "/comments";
        mvc.perform(get(endpoint).with(user(friend))).andExpect(status().isOk());
        mvc.perform(post(endpoint).with(user(friend)).contentType("application/json").content("{\"text\":\"Bom treino!\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canDelete").value(true));
        owner.setGhostMode(true);
        users.saveAndFlush(owner);
        mvc.perform(get(endpoint).with(user(friend))).andExpect(status().isNotFound());
        owner.setGhostMode(false);
        users.saveAndFlush(owner);
        var workout = workouts.findById(post.getWorkoutSession().getId()).orElseThrow();
        workout.setIsPrivate(true);
        workouts.saveAndFlush(workout);
        mvc.perform(get(endpoint).with(user(friend))).andExpect(status().isNotFound());
        post.setIsRemoved(true);
        posts.saveAndFlush(post);
        mvc.perform(get(endpoint).with(user(owner))).andExpect(status().isNotFound());
    }

    @Test void exportsCurrentOpenApiWhenRequested() throws Exception {
        String output = System.getProperty("beefit.openapi.output");
        if (output == null) return;
        String json = mvc.perform(get("/v3/api-docs").with(user(owner))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of(output), json);
    }

    @Autowired FriendshipRepository friendships;

    private PostComment savedComment(User author) {
        var comment = new PostComment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setText("Ótimo treino!");
        return comments.saveAndFlush(comment);
    }

    private User newUser() {
        var result = new User();
        result.setFullName("Usuário Comentários");
        result.setUsername("comment-" + UUID.randomUUID());
        result.setEmail(UUID.randomUUID() + "@beefit.test");
        result.setPasswordHash(passwordEncoder.encode("test-password"));
        result.setBodyWeight(80F);
        result.setWeeklyGoal(3);
        result.setRole(UserRole.USER);
        return users.saveAndFlush(result);
    }
}
