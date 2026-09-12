package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.FriendshipStatus;
import com.beefit.api.exception.friendship.FriendshipNotFound;
import com.beefit.api.exception.friendship.FriendshipNotPendingException;
import com.beefit.api.exception.user.DifferentUserException;
import com.beefit.api.repository.FriendshipRepository;
import com.beefit.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private SseService sseService;

    @InjectMocks
    private FriendshipService friendshipService;

    private User requester;
    private User addressee;
    private User thirdParty;
    private Friendship pendingFriendship;
    private UUID friendshipId;

    @BeforeEach
    void setUp() {
        requester = new User();
        requester.setId(UUID.randomUUID());
        requester.setFullName("Requester User");
        requester.setUsername("requester");

        addressee = new User();
        addressee.setId(UUID.randomUUID());
        addressee.setFullName("Addressee User");
        addressee.setUsername("addressee");

        thirdParty = new User();
        thirdParty.setId(UUID.randomUUID());
        thirdParty.setFullName("Third User");
        thirdParty.setUsername("third");

        friendshipId = UUID.randomUUID();
        pendingFriendship = new Friendship();
        pendingFriendship.setId(friendshipId);
        pendingFriendship.setRequester(requester);
        pendingFriendship.setAddressee(addressee);
        pendingFriendship.setStatus(FriendshipStatus.PENDING);
    }

    @Test
    @DisplayName("Successfully cancels a pending friend request by requester")
    void cancelFriendshipRequestSuccess() {
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(pendingFriendship));

        assertDoesNotThrow(() -> friendshipService.cancelFriendshipRequest(requester, friendshipId));

        verify(friendshipRepository).delete(pendingFriendship);
    }

    @Test
    @DisplayName("Throws FriendshipNotFound when friendship does not exist")
    void cancelFriendshipRequestNotFound() {
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.empty());

        assertThrows(FriendshipNotFound.class, () -> friendshipService.cancelFriendshipRequest(requester, friendshipId));

        verify(friendshipRepository, never()).delete(pendingFriendship);
    }

    @Test
    @DisplayName("Throws DifferentUserException when user is not the requester")
    void cancelFriendshipRequestDifferentUser() {
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(pendingFriendship));

        assertThrows(DifferentUserException.class, () -> friendshipService.cancelFriendshipRequest(thirdParty, friendshipId));

        verify(friendshipRepository, never()).delete(pendingFriendship);
    }

    @Test
    @DisplayName("Throws FriendshipNotPendingException when request is not PENDING")
    void cancelFriendshipRequestNotPending() {
        pendingFriendship.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(pendingFriendship));

        assertThrows(FriendshipNotPendingException.class, () -> friendshipService.cancelFriendshipRequest(requester, friendshipId));

        verify(friendshipRepository, never()).delete(pendingFriendship);
    }
}
