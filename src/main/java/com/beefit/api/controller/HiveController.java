package com.beefit.api.controller;

import com.beefit.api.domain.Hive;
import com.beefit.api.domain.User;
import com.beefit.api.dto.hive.HiveCreationDTO;
import com.beefit.api.dto.hive.HiveRankingDTO;
import com.beefit.api.dto.hive.HiveResponseDTO;
import com.beefit.api.mapper.HiveMapper;
import com.beefit.api.service.HiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/hives/")
public class HiveController {

    private final HiveService hiveService;
    private final HiveMapper hiveMapper;

    @PostMapping("")
    public ResponseEntity<HiveResponseDTO> createNewHive(@AuthenticationPrincipal User user, @RequestBody @Valid HiveCreationDTO hiveCreationDTO){

        Hive hive = hiveService.createNewHive(user, hiveCreationDTO);

        HiveResponseDTO response = hiveMapper.toDto(hive);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("{hiveId}")
    public ResponseEntity<Void> deleteHive(@AuthenticationPrincipal User user, @PathVariable UUID hiveId){

        hiveService.deleteHive(user, hiveId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("{hiveInvite}/join")
    public ResponseEntity<HiveResponseDTO> joinHive(@AuthenticationPrincipal User user, @PathVariable String hiveInvite){

        Hive hive = hiveService.joinHive(user, hiveInvite);

        HiveResponseDTO response = hiveMapper.toDto(hive);

        return ResponseEntity.ok(response);
    }

    @GetMapping("{hiveId}/getData")
    public ResponseEntity<List<HiveRankingDTO>> getHiveData(@PathVariable UUID hive){

        List<HiveRankingDTO> response = hiveService.getHiveRanking(hive);

        return ResponseEntity.ok(response);
    }

}
