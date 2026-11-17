-- Contas criadas via login social nascem sem peso e meta semanal (o provedor nao
-- os fornece); o usuario os informa na tela "Complete seu cadastro".
ALTER TABLE public.users
    ALTER COLUMN body_weight DROP NOT NULL,
    ALTER COLUMN weekly_goal DROP NOT NULL;
