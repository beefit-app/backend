package com.beefit.api.service;

import com.beefit.api.domain.Hive;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.hive.HiveCreationDTO;
import com.beefit.api.dto.hive.HiveRankingDTO;
import com.beefit.api.exception.hive.ExceededHiveLimitException;
import com.beefit.api.exception.hive.HiveNotFoundException;
import com.beefit.api.exception.hive.InvalidTimePeriodException;
import com.beefit.api.exception.hive.UserAlreadyInHiveException;
import com.beefit.api.exception.user.PermissionException;
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
        Hive hive = hiveRepository.findById(hiveId).orElseThrow(HiveNotFoundException::new);
        return getRankingForHive(hive);
    }

    @Transactional
    public void leaveHive(User user, UUID hiveId) {
        Hive hive = hiveRepository.findById(hiveId).orElseThrow(HiveNotFoundException::new);
        
        boolean isMember = hive.getMembers().stream()
                .anyMatch(member -> member.getId().equals(user.getId()));
        if (!isMember) {
            throw new PermissionException();
        }

        if (hive.getCreator().getId().equals(user.getId())) {
            if (hive.getMembers().size() <= 1) {
                hiveRepository.delete(hive);
            } else {
                List<HiveRankingDTO> ranking = getRankingForHive(hive);
                
                UUID newCreatorId = ranking.stream()
                        .map(HiveRankingDTO::userId)
                        .filter(id -> !id.equals(user.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Nenhum outro membro encontrado para assumir a colmeia"));
                
                User newCreator = hive.getMembers().stream()
                        .filter(m -> m.getId().equals(newCreatorId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Membro sucessor não encontrado no grupo"));
                
                hive.setCreator(newCreator);
                hive.getMembers().removeIf(m -> m.getId().equals(user.getId()));
                hiveRepository.save(hive);
            }
        } else {
            hive.getMembers().removeIf(member -> member.getId().equals(user.getId()));
            hiveRepository.save(hive);
        }
    }

    public List<Hive> getMyHives(User user) {
        return hiveRepository.findAllByMembersContaining(user);
    }

    public Hive getHive(User user, UUID hiveId) {
        Hive hive = hiveRepository.findById(hiveId).orElseThrow(HiveNotFoundException::new);
        boolean member = hive.getMembers().stream()
                .anyMatch(current -> current.getId() != null && current.getId().equals(user.getId()));
        if (!member) throw new PermissionException();
        return hive;
    }

    // Funções Auxiliares
    private void validateUserNotAlreadyInHive(User user, Hive hive){

        if(hiveRepository.findAllByMembersContaining(user).contains(hive)){
            throw new UserAlreadyInHiveException();
        }
    }

    private List<HiveRankingDTO> getRankingForHive(Hive hive) {
        return hiveRepository.getRankingForHive(
                hive.getId(),
                WorkoutStatus.FINISHED,
                hive.getStartDate().atStartOfDay(),
                hive.getEndDate().plusDays(1).atStartOfDay());
    }

    private void validateHiveOwnership(User user, Hive hive){

        if(!hive.getCreator().getId().equals(user.getId())){
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
