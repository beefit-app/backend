package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;
import com.beefit.api.dto.friendship.FriendshipResponseDTO;
import com.beefit.api.dto.social.SocialUserResponseDTO;
import com.beefit.api.domain.enums.FriendshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/api/social/friends")
@RequiredArgsConstructor
public class FriendshipController implements GenericController {

    private final FriendshipService friendshipService;

    @PostMapping("/request/{addresseeId}")
    public ResponseEntity<FriendshipResponseDTO> sendFriendRequest (@AuthenticationPrincipal User user, @PathVariable UUID addresseeId){
        return ResponseEntity.status(HttpStatus.CREATED).body(friendshipService.createFriendship(user, addresseeId));
    }

    @PostMapping("/{friendshipId}/accept")
    public ResponseEntity<FriendshipResponseDTO> acceptRequest (@AuthenticationPrincipal User user, @PathVariable UUID friendshipId){
        return ResponseEntity.ok(friendshipService.acceptRequest(user, friendshipId));
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

    @GetMapping
    public ResponseEntity<List<FriendshipResponseDTO>> getFriends(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(friendshipService.getFriendships(user, FriendshipStatus.ACCEPTED, null));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<List<FriendshipResponseDTO>> getIncoming(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(friendshipService.getFriendships(user, FriendshipStatus.PENDING, "incoming"));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<List<FriendshipResponseDTO>> getOutgoing(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(friendshipService.getFriendships(user, FriendshipStatus.PENDING, "outgoing"));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SocialUserResponseDTO>> search(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(friendshipService.searchUsers(user, query, pageable));
    }
}
