package com.beefit.api.service;

import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.FriendshipStatus;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.dto.FriendAcceptedEventDTO;
import com.beefit.api.dto.FriendRequestEventDTO;
import com.beefit.api.dto.SocialEventDTO;
import com.beefit.api.exception.*;
import com.beefit.api.repository.FriendshipRepository;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.control.MappingControl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final SseService sseService;

    @Transactional
    public void createFriendship(User requester, UUID addresseeId){
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

        friendshipRepository.save(friendship);

        FriendRequestEventDTO friendRequestEventDTO = new FriendRequestEventDTO(
                friendship.getId(),
                requester.getId(),
                requester.getUsername(),
                friendship.getSentAt()
        );

        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.FRIEND_REQUEST_PENDING, friendRequestEventDTO)
                , friendship.getAddressee().getId());
    }

    @Transactional
    public void acceptRequest(User addressee, UUID friendshipId){
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
                addressee.getUsername(),
                friendship.getUpdatedAt()
        );

        sseService.broadcastToFriend(new SocialEventDTO<>(SocialEvent.FRIEND_REQUEST_ACCEPTED, friendAcceptedEventDTO)
                                    , friendship.getRequester().getId());
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

        friendshipRepository.delete(friendship);
    }

    public Optional<List<User>> getFriends(UUID userId){
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        List<Friendship> friendships = friendshipRepository.findAllByUserAndStatus(user, FriendshipStatus.ACCEPTED);

        List<User> friends = friendships.stream()
                .map(friendship -> friendship.getRequester().equals(user)
                ? friendship.getAddressee() :
                friendship.getRequester()).toList();

        return Optional.of(friends);
    }

}
