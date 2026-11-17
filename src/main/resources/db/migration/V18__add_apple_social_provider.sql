ALTER TABLE public.user_social_accounts
    DROP CONSTRAINT user_social_accounts_provider_check;

ALTER TABLE public.user_social_accounts
    ADD CONSTRAINT user_social_accounts_provider_check
    CHECK (provider IN ('GOOGLE', 'MICROSOFT', 'APPLE'));
