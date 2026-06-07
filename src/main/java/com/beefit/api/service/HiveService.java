package com.beefit.api.service;

import com.beefit.api.domain.Hive;
import com.beefit.api.dto.HiveRankingDTO;
import com.beefit.api.repository.HiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HiveService {

    private final HiveRepository hiveRepository;

    public List<HiveRankingDTO> getHiveRanking(UUID hiveId){

        return hiveRepository.getRankingForHive(hiveId);
    }
}
