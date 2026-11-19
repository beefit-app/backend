package com.beefit.api.service;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.PostComment;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.comment.CommentsChangedEventDTO;
import com.beefit.api.dto.comment.CreateCommentDTO;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.exception.friendship.PostNotFoundException;
import com.beefit.api.repository.PostCommentRepository;
import com.beefit.api.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceTest {
    @Mock PostCommentRepository comments;
    @Mock PostRepository posts;
    @Mock SseService sse;
    @InjectMocks PostCommentService service;
    User author;
    User owner;
    Post post;
    PostComment comment;

    @BeforeEach
    void setup() {
        author = user();
        owner = user();
        post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(owner);
        comment = new PostComment();
        comment.setId(UUID.randomUUID());
        comment.setAuthor(author);
        comment.setPost(post);
        comment.setText("Bom treino! 💪");
        comment.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void creates360CharactersAndReturnsPermissions() {
        when(posts.findVisibleByIdForUpdate(post.getId(), author.getId())).thenReturn(Optional.of(post));
        when(comments.saveAndFlush(any())).thenAnswer(call -> {
            PostComment saved = call.getArgument(0);
            saved.setId(comment.getId());
            return saved;
        });
        var result = service.create(author, post.getId(), new CreateCommentDTO("a".repeat(360)));
        assertEquals(360, result.text().length());
        assertTrue(result.canDelete());
        assertFalse(result.canReport());
    }

    @Test
    void rejects361AndBlankTextWithoutSaving() {
        when(posts.findVisibleByIdForUpdate(post.getId(), author.getId())).thenReturn(Optional.of(post));
        for (String text : new String[]{"a".repeat(361), " \n\t ", "\u2003"}) {
            assertThrows(ResponseStatusException.class, () -> service.create(author, post.getId(), new CreateCommentDTO(text)));
        }
        verify(comments, never()).saveAndFlush(any());
    }

    @Test
    void inaccessiblePostCannotBeListedOrCommented() {
        assertThrows(PostNotFoundException.class, () -> service.list(author, post.getId(), 0, 20));
        assertThrows(PostNotFoundException.class, () -> service.create(author, post.getId(), new CreateCommentDTO("Oi")));
        verifyNoInteractions(comments);
    }

    @Test
    void ownCommentCanBeDeletedAfterLosingVisibility() {
        when(comments.findByIdAndPostIdAndIsRemovedFalse(comment.getId(), post.getId())).thenReturn(Optional.of(comment));
        service.delete(author, post.getId(), comment.getId());
        assertTrue(comment.isRemoved());
        verify(posts, never()).findVisibleByIdForUpdate(any(), any());
    }

    @Test
    void postOwnerCanDeleteSomeoneElsesComment() {
        when(comments.findByIdAndPostIdAndIsRemovedFalse(comment.getId(), post.getId())).thenReturn(Optional.of(comment));
        when(posts.findVisibleByIdForUpdate(post.getId(), owner.getId())).thenReturn(Optional.of(post));
        service.delete(owner, post.getId(), comment.getId());
        assertTrue(comment.isRemoved());
    }

    @Test
    void anotherFriendCannotDeleteTheComment() {
        User other = user();
        when(comments.findByIdAndPostIdAndIsRemovedFalse(comment.getId(), post.getId())).thenReturn(Optional.of(comment));
        when(posts.findVisibleByIdForUpdate(post.getId(), other.getId())).thenReturn(Optional.of(post));
        assertThrows(ResponseStatusException.class, () -> service.delete(other, post.getId(), comment.getId()));
        assertFalse(comment.isRemoved());
    }

    @Test
    void mismatchedPostAndCommentDoNotDeleteAnything() {
        assertThrows(ResponseStatusException.class, () -> service.delete(author, UUID.randomUUID(), comment.getId()));
        assertFalse(comment.isRemoved());
    }

    @Test
    void adminCanDeleteAndModerate() {
        User admin = user();
        admin.setRole(UserRole.ADMIN);
        when(comments.findByIdAndPostIdAndIsRemovedFalse(comment.getId(), post.getId())).thenReturn(Optional.of(comment));
        service.delete(admin, post.getId(), comment.getId());
        assertTrue(comment.isRemoved());
        when(comments.findById(comment.getId())).thenReturn(Optional.of(comment));
        service.moderate(comment.getId());
        assertTrue(comment.isRemoved());
    }

    @Test
    void listsOnlyAPageAndMarksReportPermission() {
        when(posts.findVisibleById(post.getId(), owner.getId())).thenReturn(Optional.of(post));
        when(comments.findByPostIdAndIsRemovedFalse(eq(post.getId()), any())).thenReturn(new PageImpl<>(List.of(comment)));
        var result = service.list(owner, post.getId(), 0, 20).getContent().getFirst();
        assertTrue(result.canDelete());
        assertTrue(result.canReport());
        assertEquals("Bom treino! 💪", result.text());
        assertThrows(ResponseStatusException.class, () -> service.list(owner, post.getId(), 0, 51));
    }

    @Test
    void sendsOnlyInvalidationToVisibleRecipients() {
        when(comments.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(posts.findVisibleAudienceIds(post.getId())).thenReturn(List.of(owner.getId()));
        service.moderate(comment.getId());
        var event = new SocialEventDTO<>(SocialEvent.POST_COMMENTS_CHANGED, new CommentsChangedEventDTO(post.getId()));
        verify(sse).broadcastToUsers(event, List.of(owner.getId()));
        verifyNoMoreInteractions(sse);
    }

    private User user() {
        User result = new User();
        result.setId(UUID.randomUUID());
        result.setUsername("bee");
        result.setRole(UserRole.USER);
        return result;
    }
}
