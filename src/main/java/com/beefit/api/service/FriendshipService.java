package com.beefit.api.service;

import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.FriendshipStatus;
import com.beefit.api.exception.*;
import com.beefit.api.repository.FriendshipRepository;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.control.MappingControl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional
    public void createFriendship(User requester, UUID addresseeId){
        if(addresseeId.equals(requester.getId())){
            throw new IllegalArgumentException("Você não pode adicionar a si mesmo.");
        }

        User addressee = userRepository.findById(addresseeId).orElseThrow(UserNotFoundException::new);

        boolean checkFriendship = friendshipRepository.existsFriendshipBetween(requester, addressee);

        if(checkFriendship){
            throw new FriendshipAlreadyExistException();
        }

        Friendship friendship = new Friendship();

        friendship.setRequester(requester);
        friendship.setAddressee(addressee);

        if(addressee.getGhostMode()){
            friendship.setStatus(FriendshipStatus.PENDING);
        }else{
            friendship.setStatus(FriendshipStatus.ACCEPTED);
        }

        friendshipRepository.save(friendship);
    }

    @Transactional
    public void acceptRequest(User addressee, UUID friendshipId){
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow(FriendshipNotFound::new);

        if(!addressee.equals(friendship.getAddressee())){
            throw new DifferentUserException();
        }

        if(!FriendshipStatus.PENDING.equals(friendship.getStatus())){
            throw new FriendshipNotPendingException();
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
    }

    @Transactional
    public void rejectRequest(User addressee, UUID friendshipId){
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow(FriendshipNotFound::new);

        if(!addressee.equals(friendship.getAddressee())){
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

        boolean checkUser = user.equals(friendship.getRequester()) || user.equals(friendship.getAddressee());

        if(!checkUser){
            throw new DifferentUserException();
        }

        if(!FriendshipStatus.ACCEPTED.equals(friendship.getStatus())){
            throw new FriendshipNotAcceptedException();
        }

        friendshipRepository.delete(friendship);
    }
}
