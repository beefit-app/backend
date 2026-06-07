package com.beefit.api.controller;

import com.beefit.api.domain.User;
import com.beefit.api.dto.HiveRankingDTO;
import com.beefit.api.service.HiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/hives/")
public class HiveController {

    private final HiveService hiveService;

    @PostMapping("create")
    public String createNewHive(@AuthenticationPrincipal User user){
        return "Colmeia criada";
    }

    @GetMapping("{hiveId}/getData")
    public ResponseEntity<List<HiveRankingDTO>> getHiveData(@PathVariable UUID hive){

        List<HiveRankingDTO> response = hiveService.getHiveRanking(hive);

        return ResponseEntity.ok(response);
    }

}
