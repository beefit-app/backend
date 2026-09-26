CREATE TABLE public.reports (
    id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    target_type character varying(16) NOT NULL,
    target_post_id uuid,
    target_user_id uuid,
    reason character varying(32) NOT NULL,
    details character varying(500),
    status character varying(16) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT reports_pkey PRIMARY KEY (id),
    CONSTRAINT reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.users(id),
    CONSTRAINT reports_target_post_id_fkey FOREIGN KEY (target_post_id) REFERENCES public.posts(id),
    CONSTRAINT reports_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES public.users(id),
    CONSTRAINT reports_target_type_check CHECK (target_type IN ('POST', 'USER')),
    CONSTRAINT reports_reason_check CHECK (reason IN ('SPAM', 'HARASSMENT', 'NUDITY', 'VIOLENCE', 'OTHER')),
    CONSTRAINT reports_status_check CHECK (status IN ('PENDING', 'REVIEWED', 'DISMISSED')),
    -- Exatamente uma coluna de alvo preenchida, coerente com target_type.
    CONSTRAINT reports_target_shape_check CHECK (
        (target_type = 'POST' AND target_post_id IS NOT NULL AND target_user_id IS NULL)
     OR (target_type = 'USER' AND target_user_id IS NOT NULL AND target_post_id IS NULL)
    ),
    CONSTRAINT reports_no_self_report_check CHECK (target_user_id IS NULL OR target_user_id <> reporter_id)
);

-- Índices parciais, não UNIQUE simples: as colunas de alvo são anuláveis e em
-- PostgreSQL dois NULL nunca colidem, então um UNIQUE (reporter_id, target_post_id)
-- deixaria passar denúncias duplicadas de perfil.
CREATE UNIQUE INDEX reports_unique_post_per_reporter
    ON public.reports (reporter_id, target_post_id)
    WHERE target_post_id IS NOT NULL;

CREATE UNIQUE INDEX reports_unique_user_per_reporter
    ON public.reports (reporter_id, target_user_id)
    WHERE target_user_id IS NOT NULL;

CREATE INDEX reports_status_created_at_idx ON public.reports (status, created_at);
