package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/social/friends")
@RequiredArgsConstructor
public class FriendshipController implements GenericController {

    private final FriendshipService friendshipService;

    @PostMapping("/request/{addresseeId}")
    public ResponseEntity<Void> sendFriendRequest (@AuthenticationPrincipal User user, @PathVariable UUID addresseeId){

        friendshipService.createFriendship(user, addresseeId);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{friendshipId}/accept")
    public ResponseEntity<Void> acceptRequest (@AuthenticationPrincipal User user, @PathVariable UUID friendshipId){

        friendshipService.acceptRequest(user, friendshipId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{friendshipId}/reject")
    public ResponseEntity<Void> rejectRequest (@AuthenticationPrincipal User user, @PathVariable UUID friendshipId){

        friendshipService.rejectRequest(user, friendshipId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<Void> deleteFriend (@AuthenticationPrincipal User user, @PathVariable UUID friendshipId){

        friendshipService.removeFriend(user, friendshipId);

        return ResponseEntity.noContent().build();
    }
}
