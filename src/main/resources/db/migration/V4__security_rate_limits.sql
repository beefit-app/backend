CREATE TABLE public.rate_limit_buckets (
    bucket_key varchar(96) NOT NULL,
    window_started_at timestamp with time zone NOT NULL,
    request_count integer NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT rate_limit_buckets_pkey PRIMARY KEY (bucket_key),
    CONSTRAINT rate_limit_buckets_request_count_check CHECK (request_count > 0)
);

CREATE INDEX rate_limit_buckets_expires_at_idx
    ON public.rate_limit_buckets (expires_at);
