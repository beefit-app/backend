package com.beefit.api.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.beefit.api.domain.Like;
import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import com.beefit.api.dto.feed.FeedPostResponseDTO;
import com.beefit.api.dto.feed.FeedExerciseDTO;
import com.beefit.api.dto.feed.FeedSetDTO;
import com.beefit.api.dto.feed.PostLikeDTO;
import com.beefit.api.exception.friendship.PostNotFoundException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.LikeRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final UserRepository userRepository;

    public PostService(PostRepository postRepository, LikeRepository likeRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.userRepository = userRepository;
    }

     // Recupera o feed social de forma paginada.
     // Transforma as entidades do banco (Post) nos DTOs estruturados exigidos pelo front-end,
     // incluindo metadados do treino, séries, cargas e o mapa de calor muscular.

    @Transactional
    public Page<FeedPostResponseDTO> getFriendsFeed(User currentUser, Pageable pageable) {
        // Busca os posts dos amigos (e do próprio usuário) com a otimização do JOIN FETCH
        Page<Post> postsPage = postRepository.findFriendsFeed(currentUser, pageable);

        // Mapeia a página de entidades para uma página de DTOs de resposta
        return postsPage.map(post -> convertToFeedPostResponseDTO(post, currentUser));
    }

    @Transactional
    public Page<FeedPostResponseDTO> getOwnPosts(User user, Pageable pageable) {
        return postRepository.findAllByUserAndIsRemovedFalseOrderByPublishedAtDesc(user, pageable)
                .map(post -> convertToFeedPostResponseDTO(post, user));
    }

    @Transactional
    public Page<FeedPostResponseDTO> getPostsByUsername(User currentUser, String username, Pageable pageable) {
        User target = userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
        if (target.getGhostMode()) {
            throw new UserNotFoundException();
        }

        return postRepository.findAllByUserAndIsRemovedFalseOrderByPublishedAtDesc(target, pageable)
                .map(post -> convertToFeedPostResponseDTO(post, currentUser));
    }


     // Método auxiliar que converte a entidade Post e suas dependências no DTO do Feed.

    private FeedPostResponseDTO convertToFeedPostResponseDTO(Post post, User currentUser) {
        User author = post.getUser();
        var session = post.getWorkoutSession();

        // Extrai a lista de grupos musculares e converte os enums para String (Mapa de Calor)
        List<String> bodyHeatMap = session.getMuscleGroupsList().stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        // Mapeia a lista detalhada de exercícios da sessão
        List<FeedExerciseDTO> exerciseDTOs = session.getExercises().stream()
                .map(sessionExercise -> {

                    // Mapeia as séries (sets) realizadas dentro de cada exercício
                    List<FeedSetDTO> setDTOs = sessionExercise.getSets().stream()
                            .map(set -> new FeedSetDTO(
                                    set.getId(),
                                    set.getOrder(), // Ordem sequencial da série
                                    set.getWeightKg(),        // Carga utilizada em kg
                                    set.getReps()           // Repetições executadas
                            ))
                            .collect(Collectors.toList());

                    return new FeedExerciseDTO(
                            sessionExercise.getExercise().getId(),   // ID do catálogo (Wger ou dataset Ryan)
                            sessionExercise.getExercise().getName(), // Nome do exercício
                            sessionExercise.getOrder(),             // Ordem do exercício
                            setDTOs
                    );
                })
                .collect(Collectors.toList());

        // Constrói o envelope principal do card do feed
        return new FeedPostResponseDTO(
                post.getId(),
                author.getId(),
                author.getUsername(),
                null, // Caso o time adicione link de foto de perfil no futuro, o gancho está aqui
                post.getPublishedAt(),
                post.getTotalLikes(),
                likeRepository.existsByLikedByAndPost(currentUser, post),
                post.getTitle(),
                post.getPhotoMediaId() == null ? null : "/api/media/" + post.getPhotoMediaId(),
                session.getDurationSeconds(),
                session.getEstimatedCalories(),
                bodyHeatMap,
                exerciseDTOs
        );
    }

    @Transactional
    public PostLikeDTO toggleLike(UUID userId, UUID postId) {
        Post post = postRepository.findByIdAndIsRemovedFalse(postId).orElseThrow(PostNotFoundException::new);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        Boolean isPostLikedAlready = likeRepository.existsByLikedByAndPost(user, post);

        if (isPostLikedAlready) {
            post.decrementLikes();
            likeRepository.deleteByLikedByAndPost(user, post);
        } else {
            Like like = new Like();
            like.setLikedBy(user);
            like.setPost(post);
            like.setLikedAt(LocalDateTime.now());

            post.incrementLikes();
            post.getLikes().add(like);

            postRepository.save(post);
        }

        // retorna o contrário da variável de post já curtido pois o valor foi invertido
        // após o processamento da função
        return new PostLikeDTO(!isPostLikedAlready, post.getTotalLikes());
    }
}
