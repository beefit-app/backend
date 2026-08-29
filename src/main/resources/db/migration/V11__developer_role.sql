ALTER TABLE public.users
    DROP CONSTRAINT users_role_check;

ALTER TABLE public.users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'DEVELOPER', 'USER'));
