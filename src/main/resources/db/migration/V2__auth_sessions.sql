CREATE TABLE public.auth_sessions (
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    last_used_at timestamp(6) without time zone NOT NULL,
    revoked_at timestamp(6) without time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    device_id character varying(255) NOT NULL,
    refresh_token_hash character varying(255) NOT NULL,
    CONSTRAINT auth_sessions_pkey PRIMARY KEY (id),
    CONSTRAINT auth_sessions_refresh_token_hash_key UNIQUE (refresh_token_hash),
    CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);

CREATE INDEX idx_auth_sessions_user_device
    ON public.auth_sessions (user_id, device_id);

CREATE INDEX idx_auth_sessions_expires_at
    ON public.auth_sessions (expires_at);
