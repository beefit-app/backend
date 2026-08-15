package com.beefit.api.repository;

import com.beefit.api.domain.Hive;
import com.beefit.api.domain.User;
import com.beefit.api.dto.hive.HiveRankingDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import com.beefit.api.domain.enums.WorkoutStatus;

@Repository
public interface HiveRepository extends JpaRepository<Hive, UUID> {

    // Busca a colmeia através do convite
    Optional<Hive> findByInviteCode(String inviteCode);

    // Conta quantas colmeias o usuário já é membro
    // Ideal para chamar no Service antes de executar um hive.addMember(user) e conferir se ele já esta em mais 2
    @Query("SELECT COUNT(h) FROM Hive h JOIN h.members m WHERE m = :user")
    long countActiveHivesByUser(@Param("user") User user);

    // Retorna todas colmeias que o usuário participa
    List<Hive> findAllByMembersContaining(User user);

    // Busca todas colmeias criadas pelo usuário
    List<Hive> findAllByCreator(User creator);

    // Conferir essa query para posterior otimização
    // Agrupa os membros da colmeia e conta quantos treinos válidos eles têm dentro do período de disputa.
    @Query("SELECT new com.beefit.api.dto.hive.HiveRankingDTO(m.id, m.fullName, m.username, m.profilePicture, COUNT(ws)) " +
            "FROM Hive h " +
            "JOIN h.members m " +
            "LEFT JOIN WorkoutSession ws ON ws.user = m " +
            "   AND ws.status = :status " +
            "   AND ws.startedAt >= :startInclusive " +
            "   AND ws.startedAt < :endExclusive " +
            "WHERE h.id = :hiveId " +
            "GROUP BY m " +
            "ORDER BY COUNT(ws) DESC, m.offensiveHoneys ASC") // A ofensiva está como critério de desempate
    List<HiveRankingDTO> getRankingForHive(
            @Param("hiveId") UUID hiveId,
            @Param("status") WorkoutStatus status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    // Pega a pontuação de um usuário específico na colmeia
    @Query("SELECT COUNT(ws) " +
            "FROM Hive h " +
            "JOIN h.members m " +
            // Fazemos o JOIN com a sessão de treino garantindo que ela pertence ao membro
            // e que ocorreu dentro da janela de datas da colmeia
            "LEFT JOIN WorkoutSession ws ON ws.user = m " +
            "   AND ws.status = :status " +
            "   AND ws.startedAt >= :startInclusive " +
            "   AND ws.startedAt < :endExclusive " +
            "WHERE h.id = :hiveId " +
            "  AND m.id = :userId")
    Long getUserScoreInHive(
            @Param("hiveId") UUID hiveId,
            @Param("userId") UUID userId,
            @Param("status") WorkoutStatus status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}
