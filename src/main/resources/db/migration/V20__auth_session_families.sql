ALTER TABLE public.auth_sessions ADD COLUMN family_id uuid;

-- Não há histórico de linhagem antes desta migração. Cada registro existente
-- começa como sua própria raiz para não confundir rotações com novos logins.
UPDATE public.auth_sessions SET family_id = id;

ALTER TABLE public.auth_sessions ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_auth_sessions_open_family
    ON public.auth_sessions (user_id, family_id) WHERE revoked_at IS NULL;
