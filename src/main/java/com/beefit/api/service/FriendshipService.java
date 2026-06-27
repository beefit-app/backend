package com.beefit.api.service;

import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.FriendshipStatus;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.dto.friendship.FriendAcceptedEventDTO;
import com.beefit.api.dto.friendship.FriendRemovedEventDTO;
import com.beefit.api.dto.friendship.FriendRequestEventDTO;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.exception.friendship.FriendshipAlreadyExistException;
import com.beefit.api.exception.friendship.FriendshipNotAcceptedException;
import com.beefit.api.exception.friendship.FriendshipNotFound;
import com.beefit.api.exception.friendship.FriendshipNotPendingException;
import com.beefit.api.exception.user.DifferentUserException;
import com.beefit.api.exception.user.SameUserException;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.repository.FriendshipRepository;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.beefit.api.dto.friendship.FriendshipResponseDTO;
import com.beefit.api.dto.social.SocialUserResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final SseService sseService;

    @Transactional
    public FriendshipResponseDTO createFriendship(User requester, UUID addresseeId){
        if(addresseeId.equals(requester.getId())){
            throw new SameUserException();
        }

        User addressee = userRepository.findById(addresseeId).orElseThrow(UserNotFoundException::new);

        boolean checkFriendship = friendshipRepository.existsFriendshipBetween(requester, addressee);

        if(checkFriendship){
            throw new FriendshipAlreadyExistException();
        }

        Friendship friendship = new Friendship();

        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(FriendshipStatus.PENDING);

        Friendship saved = friendshipRepository.saveAndFlush(friendship);

        FriendRequestEventDTO friendRequestEventDTO = new FriendRequestEventDTO(
                saved.getId(),
                requester.getId(),
                requester.getFullName(),
                requester.getUsername(),
                requester.getProfilePicture(),
                saved.getSentAt()
        );

        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.FRIEND_REQUEST_PENDING, friendRequestEventDTO)
                , saved.getAddressee().getId());
        return toResponse(saved, requester);
    }

    @Transactional
    public FriendshipResponseDTO acceptRequest(User addressee, UUID friendshipId){
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow(FriendshipNotFound::new);

        if(!addressee.getId().equals(friendship.getAddressee().getId())){
            throw new DifferentUserException();
        }

        if(!FriendshipStatus.PENDING.equals(friendship.getStatus())){
            throw new FriendshipNotPendingException();
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);

        FriendAcceptedEventDTO friendAcceptedEventDTO = new FriendAcceptedEventDTO(
                friendshipId,
                addressee.getId(),
                addressee.getFullName(),
                addressee.getUsername(),
                addressee.getProfilePicture(),
                friendship.getUpdatedAt()
        );

        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.FRIEND_REQUEST_ACCEPTED, friendAcceptedEventDTO)
                                    , friendship.getRequester().getId());
        return toResponse(friendship, addressee);
    }

    @Transactional
    public void rejectRequest(User addressee, UUID friendshipId){
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow(FriendshipNotFound::new);

        if(!addressee.getId().equals(friendship.getAddressee().getId())){
            throw new DifferentUserException();
        }

        if(!FriendshipStatus.PENDING.equals(friendship.getStatus())){
            throw new FriendshipNotPendingException();
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void removeFriend(User user, UUID friendshipID){
        Friendship friendship = friendshipRepository.findById(friendshipID).orElseThrow(FriendshipNotFound::new);

        boolean checkUser = (user.getId().equals(friendship.getRequester().getId())) || (user.getId().equals(friendship.getAddressee().getId()));

        if(!checkUser){
            throw new DifferentUserException();
        }

        if(!FriendshipStatus.ACCEPTED.equals(friendship.getStatus())){
            throw new FriendshipNotAcceptedException();
        }

        User requester = friendship.getRequester();
        User addressee = friendship.getAddressee();

        friendshipRepository.delete(friendship);

        FriendRemovedEventDTO requesterEvent = new FriendRemovedEventDTO(
                friendshipID,
                addressee.getId(),
                addressee.getFullName(),
                addressee.getUsername(),
                addressee.getProfilePicture(),
                friendship.getUpdatedAt()
        );
        FriendRemovedEventDTO addresseeEvent = new FriendRemovedEventDTO(
                friendshipID,
                requester.getId(),
                requester.getFullName(),
                requester.getUsername(),
                requester.getProfilePicture(),
                friendship.getUpdatedAt()
        );

        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.FRIEND_REMOVED, requesterEvent), requester.getId());
        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.FRIEND_REMOVED, addresseeEvent), addressee.getId());
    }

    public Optional<List<User>> getFriends(UUID userId){
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        List<Friendship> friendships = friendshipRepository.findAllByUserIdAndStatus(user.getId(), FriendshipStatus.ACCEPTED);

        List<User> friends = friendships.stream()
                .map(friendship -> isSameUser(friendship.getRequester(), user)
                ? friendship.getAddressee() :
                friendship.getRequester()).toList();

        return Optional.of(friends);
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponseDTO> getFriendships(User user, FriendshipStatus status, String direction) {
        return friendshipRepository.findAllByUserIdAndStatus(user.getId(), status).stream()
                .filter(friendship -> direction == null || (direction.equals("incoming") && isSameUser(friendship.getAddressee(), user))
                        || (direction.equals("outgoing") && isSameUser(friendship.getRequester(), user)))
                .map(friendship -> toResponse(friendship, user)).toList();
    }

    @Transactional(readOnly = true)
    public Page<SocialUserResponseDTO> searchUsers(User user, String query, Pageable pageable) {
        return userRepository.searchSocialUsers(user.getId(), query == null ? "" : query.trim(), pageable)
                .map(this::toSocialUser);
    }

    private FriendshipResponseDTO toResponse(Friendship friendship, User viewer) {
        boolean requester = isSameUser(friendship.getRequester(), viewer);
        return new FriendshipResponseDTO(friendship.getId(), toSocialUser(requester ? friendship.getAddressee() : friendship.getRequester()),
                friendship.getStatus().name(), requester ? "outgoing" : "incoming", friendship.getSentAt());
    }

    private boolean isSameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }

    private SocialUserResponseDTO toSocialUser(User user) {
        return new SocialUserResponseDTO(user.getId(), user.getFullName(), user.getUsername(), user.getProfilePicture());
    }

}
