package com.beefit.api.service;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.PostComment;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.dto.comment.CommentResponseDTO;
import com.beefit.api.dto.comment.CommentsChangedEventDTO;
import com.beefit.api.dto.comment.CreateCommentDTO;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.exception.friendship.PostNotFoundException;
import com.beefit.api.repository.PostCommentRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.utils.log.LogAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostCommentService {
    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final FriendshipService friendshipService;
    private final SseService sseService;

    @Transactional(readOnly = true)
    public Page<CommentResponseDTO> list(User viewer, UUID postId, int page, int size) {
        Post post = postRepository.findVisibleById(postId, viewer.getId()).orElseThrow(PostNotFoundException::new);
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
        }
        return commentRepository.findByPostIdAndIsRemovedFalse(postId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(comment -> toResponse(comment, viewer, post));
    }

    @Transactional(readOnly = true)
    public long count(User viewer, UUID postId) {
        postRepository.findVisibleById(postId, viewer.getId()).orElseThrow(PostNotFoundException::new);
        return commentRepository.countByPostIdAndIsRemovedFalse(postId);
    }

    @Transactional
    public CommentResponseDTO create(User author, UUID postId, CreateCommentDTO request) {
        Post post = postRepository.findVisibleByIdForUpdate(postId, author.getId()).orElseThrow(PostNotFoundException::new);
        String text = request.text();
        if (text == null || text.isBlank() || text.length() > 360) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O comentário deve ter entre 1 e 360 caracteres.");
        }
        PostComment comment = new PostComment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setText(text.strip());
        comment = commentRepository.saveAndFlush(comment);
        notifyChanged(post);
        return toResponse(comment, author, post);
    }

    @Transactional
    public void delete(User viewer, UUID postId, UUID commentId) {
        // A autoria permite apagar o próprio texto mesmo após o fim da amizade.
        // Não devolvemos o conteúdo nem concedemos acesso aos demais comentários.
        PostComment comment = commentRepository.findByIdAndPostIdAndIsRemovedFalse(commentId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentário não encontrado."));
        Post post = comment.getPost();
        if (!comment.getAuthor().getId().equals(viewer.getId()) && viewer.getRole() != UserRole.ADMIN) {
            post = postRepository.findVisibleByIdForUpdate(postId, viewer.getId()).orElseThrow(PostNotFoundException::new);
            if (!post.getUser().getId().equals(viewer.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode excluir este comentário.");
            }
        }
        comment.setRemoved(true);
        notifyChanged(post);
    }

    @Transactional
    @LogAudit(action = "REMOVE_COMMENT", description = "Remove comentário denunciado.")
    public void moderate(UUID commentId) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentário não encontrado."));
        comment.setRemoved(true);
        notifyChanged(comment.getPost());
    }

    private CommentResponseDTO toResponse(PostComment comment, User viewer, Post post) {
        User author = comment.getAuthor();
        boolean own = author.getId().equals(viewer.getId());
        return new CommentResponseDTO(comment.getId(), author.getId(), author.getUsername(), author.getProfilePicture(),
                author.hasDeveloperBadge(), comment.getText(), comment.getCreatedAt(),
                own || post.getUser().getId().equals(viewer.getId()) || viewer.getRole() == UserRole.ADMIN, !own);
    }

    private void notifyChanged(Post post) {
        List<User> recipients = new ArrayList<>(friendshipService.getFriends(post.getUser().getId()).orElseGet(List::of));
        recipients.add(post.getUser());
        var event = new SocialEventDTO<>(SocialEvent.POST_COMMENTS_CHANGED, new CommentsChangedEventDTO(post.getId()));
        // Não enviar nem mesmo a invalidação para quem perdeu a visibilidade.
        recipients.stream().map(User::getId).distinct()
                .filter(id -> postRepository.findVisibleById(post.getId(), id).isPresent())
                .forEach(id -> sseService.broadcastToFriend(event, id));
    }
}
