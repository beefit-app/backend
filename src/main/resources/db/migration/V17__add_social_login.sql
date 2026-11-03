ALTER TABLE public.users
    ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE public.user_social_accounts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    provider character varying(20) NOT NULL,
    provider_user_id character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT user_social_accounts_pkey PRIMARY KEY (id),
    CONSTRAINT user_social_accounts_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT user_social_accounts_provider_check
        CHECK (provider IN ('GOOGLE', 'MICROSOFT')),
    CONSTRAINT user_social_accounts_provider_provider_user_id_key
        UNIQUE (provider, provider_user_id),
    CONSTRAINT user_social_accounts_user_id_provider_key
        UNIQUE (user_id, provider)
);
