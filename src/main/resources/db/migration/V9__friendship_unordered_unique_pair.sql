-- Mantém somente uma relação por par caso uma base anterior tenha recebido
-- convites simultâneos em direções opostas. Relações aceitas têm precedência.
WITH ranked_friendships AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY LEAST(requester_id, addressee_id),
                            GREATEST(requester_id, addressee_id)
               ORDER BY CASE WHEN status = 'ACCEPTED' THEN 0 ELSE 1 END,
                        sent_at NULLS LAST,
                        id
           ) AS duplicate_rank
    FROM friendship
)
DELETE FROM friendship
USING ranked_friendships
WHERE friendship.id = ranked_friendships.id
  AND ranked_friendships.duplicate_rank > 1;

ALTER TABLE friendship
    DROP CONSTRAINT IF EXISTS friendship_requester_id_addressee_id_key;

CREATE UNIQUE INDEX friendship_users_unordered_key
    ON friendship (
        LEAST(requester_id, addressee_id),
        GREATEST(requester_id, addressee_id)
    );
