package com.beefit.api.repository;

import com.beefit.api.domain.Friendship;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    // Confere se já existe alguma relação de amizade ou convite entre dois usuários
    // Vai ser útil na hora da criação de um novo convite, para impedir que o usuário B envie um
    // convite para o usuário A caso já exista um convite de A para B
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END " +
            "FROM Friendship f " +
            "WHERE (f.requester = :userA AND f.addressee = :userB) " +
            "   OR (f.requester = :userB AND f.addressee = :userA)")
    boolean existsFriendshipBetween(@Param("userA") User userA, @Param("userB") User userB);

    // Busca uma relação de convite ou amizade entre dois usuários, independente de quem enviou
    @Query("SELECT f FROM Friendship f " +
            "WHERE (f.requester = :userA AND f.addressee = :userB) " +
            "   OR (f.requester = :userB AND f.addressee = :userA)")
    Optional<Friendship> findFriendshipBetween(@Param("userA") User userA, @Param("userB") User userB);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
            "WHERE f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED AND (" +
            "(f.requester.id = :userAId AND f.addressee.id = :userBId) OR " +
            "(f.requester.id = :userBId AND f.addressee.id = :userAId))")
    boolean existsAcceptedFriendshipBetween(@Param("userAId") UUID userAId, @Param("userBId") UUID userBId);

    // Busca todas as amizades ou convites de um usuário
    @Query("SELECT f FROM Friendship f " +
            "WHERE (f.requester.id = :userId OR f.addressee.id = :userId) " +
            "  AND f.status = :status")
    List<Friendship> findAllByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    // Busca somente os convites recebidos pelo usuário
    // Pode ser combinado com o status de pendente para ver os convites não aceitos ainda por ele
    List<Friendship> findAllByAddresseeAndStatus(User addressee, FriendshipStatus status);

    // Busca somente os convites enviados pelo usuário
    // Pode ser combinado com o status de pendente para ver os convites não aceitos ou já aceitos, por exemplo
    List<Friendship> findAllByRequesterAndStatus(User requester, FriendshipStatus status);
}
