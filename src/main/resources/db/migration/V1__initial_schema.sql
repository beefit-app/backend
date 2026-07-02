-- Baseline gerado do modelo JPA PostgreSQL em 2026-06-22.
CREATE TABLE public.audit_logs (
    "timestamp" timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    action character varying(255) NOT NULL,
    admin_username character varying(255) NOT NULL,
    description character varying(255) NOT NULL,
    details text
);



CREATE TABLE public.card (
    attack_power double precision NOT NULL,
    body_weight_at_generation double precision NOT NULL,
    is_personal_record boolean NOT NULL,
    generated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    inventory_id uuid NOT NULL,
    workout_session_id uuid NOT NULL,
    card_rarity character varying(255) NOT NULL,
    muscle_group character varying(255) NOT NULL,
    CONSTRAINT card_card_rarity_check CHECK (((card_rarity)::text = ANY ((ARRAY['COMMON'::character varying, 'RARE'::character varying, 'EPIC'::character varying, 'LEGENDARY'::character varying])::text[]))),
    CONSTRAINT card_muscle_group_check CHECK (((muscle_group)::text = ANY ((ARRAY['CHEST'::character varying, 'BACK'::character varying, 'LEGS'::character varying, 'GLUTES'::character varying, 'SHOULDERS'::character varying, 'ARMS'::character varying, 'CORE'::character varying, 'TRAPS'::character varying])::text[])))
);



CREATE TABLE public.exercises (
    id uuid NOT NULL,
    instructions character varying(1000),
    name character varying(255) NOT NULL,
    primary_muscle_groups character varying[],
    secondary_muscle_groups character varying[]
);



CREATE TABLE public.friendship (
    sent_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    addressee_id uuid,
    id uuid NOT NULL,
    requester_id uuid,
    status character varying(255),
    CONSTRAINT friendship_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'BLOCKED'::character varying])::text[])))
);



CREATE TABLE public.hive_members (
    hive_id uuid NOT NULL,
    user_id uuid NOT NULL
);



CREATE TABLE public.hives (
    end_date date NOT NULL,
    start_date date NOT NULL,
    created_at timestamp(6) without time zone,
    creator_id uuid NOT NULL,
    id uuid NOT NULL,
    invite_code character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);



CREATE TABLE public.inventory (
    total_cards integer,
    id uuid NOT NULL,
    user_id uuid NOT NULL
);



CREATE TABLE public.post_likes (
    liked_at timestamp(6) without time zone,
    id uuid NOT NULL,
    post_id uuid,
    user_id uuid
);



CREATE TABLE public.posts (
    is_removed boolean,
    total_likes integer,
    published_at timestamp(6) without time zone,
    id uuid NOT NULL,
    photo_media_id uuid,
    user_id uuid NOT NULL,
    workout_session_id uuid NOT NULL,
    title character varying(255)
);



CREATE TABLE public.session_exercises (
    exercise_order integer NOT NULL,
    exercise_id uuid NOT NULL,
    id uuid NOT NULL,
    workout_id uuid NOT NULL
);



CREATE TABLE public.sets (
    is_personal_record boolean NOT NULL,
    reps integer NOT NULL,
    set_order integer NOT NULL,
    weight_kg double precision NOT NULL,
    exercise_id uuid,
    id uuid NOT NULL
);



CREATE TABLE public.users (
    body_weight real NOT NULL,
    cards_count integer NOT NULL,
    ghost_mode boolean NOT NULL,
    offensive_honeys integer NOT NULL,
    weekly_goal integer NOT NULL,
    weekly_workouts_count integer NOT NULL,
    workouts_count integer NOT NULL,
    banned_until timestamp(6) without time zone,
    id uuid NOT NULL,
    full_name character varying(100) NOT NULL,
    biography character varying(500),
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    profile_picture character varying(255),
    role character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    CONSTRAINT users_offensive_honeys_check CHECK ((offensive_honeys >= 0)),
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'USER'::character varying])::text[]))),
    CONSTRAINT users_weekly_goal_check CHECK (((weekly_goal >= 1) AND (weekly_goal <= 7)))
);



CREATE TABLE public.workout_media (
    consumed boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    content_type character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    content oid NOT NULL
);



CREATE TABLE public.workout_sessions (
    duration_seconds integer,
    estimated_calories double precision,
    is_private boolean NOT NULL,
    should_post_workout boolean NOT NULL,
    total_volume double precision,
    finished_at timestamp(6) without time zone,
    started_at timestamp(6) without time zone NOT NULL,
    card_id uuid,
    id uuid NOT NULL,
    user_id uuid,
    status character varying(255) NOT NULL,
    title character varying(255),
    muscle_groups character varying[],
    CONSTRAINT workout_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'FINISHED'::character varying, 'CANCELED'::character varying])::text[])))
);



CREATE TABLE public.workout_template_exercises (
    exercise_order integer NOT NULL,
    catalog_exercise_id uuid NOT NULL,
    id uuid NOT NULL,
    workout_template_id uuid NOT NULL
);



CREATE TABLE public.workout_templates (
    friend_source_id uuid,
    id uuid NOT NULL,
    user_id uuid,
    name character varying(255) NOT NULL,
    muscle_groups character varying[]
);



ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.card
    ADD CONSTRAINT card_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.card
    ADD CONSTRAINT card_workout_session_id_key UNIQUE (workout_session_id);



ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT exercises_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.friendship
    ADD CONSTRAINT friendship_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.friendship
    ADD CONSTRAINT friendship_requester_id_addressee_id_key UNIQUE (requester_id, addressee_id);



ALTER TABLE ONLY public.hive_members
    ADD CONSTRAINT hive_members_pkey PRIMARY KEY (hive_id, user_id);



ALTER TABLE ONLY public.hives
    ADD CONSTRAINT hives_invite_code_key UNIQUE (invite_code);



ALTER TABLE ONLY public.hives
    ADD CONSTRAINT hives_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_user_id_key UNIQUE (user_id);



ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT post_likes_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT post_likes_user_id_post_id_key UNIQUE (user_id, post_id);



ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_workout_session_id_key UNIQUE (workout_session_id);



ALTER TABLE ONLY public.session_exercises
    ADD CONSTRAINT session_exercises_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.sets
    ADD CONSTRAINT sets_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);



ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);



ALTER TABLE ONLY public.workout_media
    ADD CONSTRAINT workout_media_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.workout_sessions
    ADD CONSTRAINT workout_sessions_card_id_key UNIQUE (card_id);



ALTER TABLE ONLY public.workout_sessions
    ADD CONSTRAINT workout_sessions_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.workout_template_exercises
    ADD CONSTRAINT workout_template_exercises_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.workout_templates
    ADD CONSTRAINT workout_templates_friend_source_id_key UNIQUE (friend_source_id);



ALTER TABLE ONLY public.workout_templates
    ADD CONSTRAINT workout_templates_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.friendship
    ADD CONSTRAINT fk19mckd9nov5mnli8u35lx27m8 FOREIGN KEY (addressee_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.sets
    ADD CONSTRAINT fk3ypj9hv1bfcjt5r3af40dk910 FOREIGN KEY (exercise_id) REFERENCES public.session_exercises(id);



ALTER TABLE ONLY public.session_exercises
    ADD CONSTRAINT fk4b9wt2fghgylayw7veyng8v5 FOREIGN KEY (workout_id) REFERENCES public.workout_sessions(id);



ALTER TABLE ONLY public.workout_sessions
    ADD CONSTRAINT fk5kk4fu8lo59vido5t3oychq27 FOREIGN KEY (card_id) REFERENCES public.card(id);



ALTER TABLE ONLY public.posts
    ADD CONSTRAINT fk5lidm6cqbc7u4xhqpxm898qme FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.friendship
    ADD CONSTRAINT fk608bqp7hf9dcq8ow16pndxxrf FOREIGN KEY (requester_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT fk6s70ikopm646wy54vwowsnp6d FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.session_exercises
    ADD CONSTRAINT fk7n5f7tkelbrxgc1q7ka7oacpo FOREIGN KEY (exercise_id) REFERENCES public.exercises(id);



ALTER TABLE ONLY public.workout_template_exercises
    ADD CONSTRAINT fk7qi8dfd0rshscggjpa8hgerqs FOREIGN KEY (workout_template_id) REFERENCES public.workout_templates(id);



ALTER TABLE ONLY public.card
    ADD CONSTRAINT fk94d9j9tmua62e5cxejaeyk7ll FOREIGN KEY (inventory_id) REFERENCES public.inventory(id);



ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT fka5wxsgl4doibhbed9gm7ikie2 FOREIGN KEY (post_id) REFERENCES public.posts(id);



ALTER TABLE ONLY public.workout_template_exercises
    ADD CONSTRAINT fkaip7j6grk7gk7r40xyn8wen63 FOREIGN KEY (catalog_exercise_id) REFERENCES public.exercises(id);



ALTER TABLE ONLY public.workout_templates
    ADD CONSTRAINT fkb8mq4ixu0pi7l0s9be1ibmu71 FOREIGN KEY (friend_source_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.workout_templates
    ADD CONSTRAINT fkb8o20st3g0wrlaipy25esyey8 FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.workout_media
    ADD CONSTRAINT fkfknk7vrcccbqi5u5sws3kce10 FOREIGN KEY (owner_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.workout_sessions
    ADD CONSTRAINT fkfwqciawyjntpphp080wpa37ge FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.posts
    ADD CONSTRAINT fkh03t7o1ipj7u9k0n8xlj9l2se FOREIGN KEY (workout_session_id) REFERENCES public.workout_sessions(id);



ALTER TABLE ONLY public.hive_members
    ADD CONSTRAINT fkikc7ld6dfp1xjcb64wx61flk2 FOREIGN KEY (hive_id) REFERENCES public.hives(id);



ALTER TABLE ONLY public.hive_members
    ADD CONSTRAINT fkiy8jfxf04po06udt9ecx095jr FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.card
    ADD CONSTRAINT fkkecb4vcoypn75t2ls18yod63 FOREIGN KEY (workout_session_id) REFERENCES public.workout_sessions(id);



ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT fkkgau5n0nlewg6o9lr4yibqgxj FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.hives
    ADD CONSTRAINT fkpjaj30qq2ba65upxujdw1ra3l FOREIGN KEY (creator_id) REFERENCES public.users(id);
