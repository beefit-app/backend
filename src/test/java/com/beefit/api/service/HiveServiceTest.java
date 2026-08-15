package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.Hive;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.hive.HiveRankingDTO;
import com.beefit.api.exception.user.PermissionException;
import com.beefit.api.repository.HiveRepository;

@ExtendWith(MockitoExtension.class)
class HiveServiceTest {

    @Mock
    private HiveRepository hiveRepository;

    @InjectMocks
    private HiveService hiveService;

    @Test
    void leaveHive_OnlyMember_DeletesHive() {
        UUID hiveId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        User creator = new User();
        creator.setId(creatorId);

        Hive hive = new Hive();
        hive.setId(hiveId);
        hive.setCreator(creator);
        hive.setStartDate(LocalDate.of(2026, 8, 1));
        hive.setEndDate(LocalDate.of(2026, 8, 7));
        
        Set<User> members = new HashSet<>();
        members.add(creator);
        hive.setMembers(members);

        when(hiveRepository.findById(hiveId)).thenReturn(Optional.of(hive));

        hiveService.leaveHive(creator, hiveId);

        verify(hiveRepository).delete(hive);
    }

    @Test
    void leaveHive_MultipleMembers_TransfersCreatorAndLeaves() {
        UUID hiveId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID firstPlaceId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        User creator = new User();
        creator.setId(creatorId);

        User firstPlace = new User();
        firstPlace.setId(firstPlaceId);

        User other = new User();
        other.setId(otherId);

        Hive hive = new Hive();
        hive.setId(hiveId);
        hive.setCreator(creator);
        hive.setStartDate(LocalDate.of(2026, 8, 1));
        hive.setEndDate(LocalDate.of(2026, 8, 7));

        Set<User> members = new HashSet<>();
        members.add(creator);
        members.add(firstPlace);
        members.add(other);
        hive.setMembers(members);

        when(hiveRepository.findById(hiveId)).thenReturn(Optional.of(hive));
        
        // Simula o ranking da colmeia
        // O primeiro colocado é "other", mas o segundo colocado (depois do criador) é "firstPlace"
        List<HiveRankingDTO> ranking = List.of(
            new HiveRankingDTO(creatorId, "Creator", "creator", null, 10L),
            new HiveRankingDTO(firstPlaceId, "First Place", "first", null, 8L),
            new HiveRankingDTO(otherId, "Other", "other", null, 5L)
        );
        when(hiveRepository.getRankingForHive(
                hiveId,
                WorkoutStatus.FINISHED,
                LocalDate.of(2026, 8, 1).atStartOfDay(),
                LocalDate.of(2026, 8, 8).atStartOfDay())).thenReturn(ranking);

        hiveService.leaveHive(creator, hiveId);

        assertEquals(firstPlace, hive.getCreator());
        assertFalse(hive.getMembers().contains(creator));
        verify(hiveRepository).save(hive);
    }

    @Test
    void leaveHive_NormalMember_Leaves() {
        UUID hiveId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        User creator = new User();
        creator.setId(creatorId);

        User member = new User();
        member.setId(memberId);

        Hive hive = new Hive();
        hive.setId(hiveId);
        hive.setCreator(creator);

        Set<User> members = new HashSet<>();
        members.add(creator);
        members.add(member);
        hive.setMembers(members);

        when(hiveRepository.findById(hiveId)).thenReturn(Optional.of(hive));

        hiveService.leaveHive(member, hiveId);

        assertFalse(hive.getMembers().contains(member));
        verify(hiveRepository).save(hive);
    }

    @Test
    void leaveHive_NonMember_ThrowsPermissionException() {
        UUID hiveId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();

        User creator = new User();
        creator.setId(creatorId);

        User nonMember = new User();
        nonMember.setId(nonMemberId);

        Hive hive = new Hive();
        hive.setId(hiveId);
        hive.setCreator(creator);

        Set<User> members = new HashSet<>();
        members.add(creator);
        hive.setMembers(members);

        when(hiveRepository.findById(hiveId)).thenReturn(Optional.of(hive));

        assertThrows(PermissionException.class, () -> hiveService.leaveHive(nonMember, hiveId));
    }
}
