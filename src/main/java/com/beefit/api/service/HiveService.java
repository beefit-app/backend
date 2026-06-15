package com.beefit.api.service;

import com.beefit.api.domain.Hive;
import com.beefit.api.domain.User;
import com.beefit.api.dto.hive.HiveCreationDTO;
import com.beefit.api.dto.hive.HiveRankingDTO;
import com.beefit.api.exception.*;
import com.beefit.api.mapper.HiveMapper;
import com.beefit.api.repository.HiveRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HiveService {

    private final HiveRepository hiveRepository;
    private final HiveMapper hiveMapper;

    @Transactional
    public Hive createNewHive(User user, HiveCreationDTO hiveCreationDTO) {

        validateHiveDate(hiveCreationDTO);
        validateUserHiveLimit(user);

        Hive hive = hiveMapper.toEntity(hiveCreationDTO);

        hive.setCreator(user);
        hive.addMember(user);
        hive.setInviteCode(generateInviteCode());

        return hiveRepository.save(hive);
    }

    @Transactional
    public void deleteHive(User user, UUID hiveId) {

        Hive hive = hiveRepository.findById(hiveId)
                .orElseThrow(HiveNotFoundException::new);

        validateHiveOwnership(user, hive);

        hiveRepository.delete(hive);
    }

    @Transactional
    public Hive joinHive(User user, String hiveInvite){

        validateUserHiveLimit(user);

        Hive hive = hiveRepository.findByInviteCode(hiveInvite)
                .orElseThrow(HiveNotFoundException::new);

        validateUserNotAlreadyInHive(user, hive);

        hive.addMember(user);

        return hive;
    }

    public List<HiveRankingDTO> getHiveRanking(UUID hiveId){

        return hiveRepository.getRankingForHive(hiveId);
    }

    // Funções Auxiliares
    private void validateUserNotAlreadyInHive(User user, Hive hive){

        if(hiveRepository.findAllByMembersContaining(user).contains(hive)){
            throw new UserAlreadyInHiveException();
        }
    }

    private void validateHiveOwnership(User user, Hive hive){

        if(hive.getCreator() != user){
            throw new PermissionException();
        }
    }

    private void validateUserHiveLimit(User user){

        if(hiveRepository.countActiveHivesByUser(user) >= 2){
            throw new ExceededHiveLimitException();
        }
    }

    private void validateHiveDate(HiveCreationDTO dto){

        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        if(dto.startDate().isAfter(dto.endDate()) || dto.startDate().isBefore(hoje)){
            throw new InvalidTimePeriodException();
        }
    }

    private String generateInviteCode(){
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
