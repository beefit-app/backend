CREATE TABLE post_comments (
    id uuid PRIMARY KEY,
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text varchar(360) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    is_removed boolean NOT NULL DEFAULT false,
    CONSTRAINT post_comments_text_check CHECK (length(trim(text)) BETWEEN 1 AND 360)
);
CREATE INDEX post_comments_page_idx ON post_comments(post_id, created_at DESC, id DESC) WHERE is_removed = false;
CREATE INDEX post_comments_author_idx ON post_comments(author_id);

ALTER TABLE reports ADD COLUMN target_comment_id uuid REFERENCES post_comments(id) ON DELETE CASCADE;
ALTER TABLE reports DROP CONSTRAINT reports_target_type_check;
ALTER TABLE reports DROP CONSTRAINT reports_target_shape_check;
ALTER TABLE reports ADD CONSTRAINT reports_target_type_check CHECK (target_type IN ('POST', 'USER', 'COMMENT'));
ALTER TABLE reports ADD CONSTRAINT reports_target_shape_check CHECK (
    (target_type = 'POST' AND target_post_id IS NOT NULL AND target_user_id IS NULL AND target_comment_id IS NULL)
 OR (target_type = 'USER' AND target_user_id IS NOT NULL AND target_post_id IS NULL AND target_comment_id IS NULL)
 OR (target_type = 'COMMENT' AND target_comment_id IS NOT NULL AND target_post_id IS NULL AND target_user_id IS NULL)
);
CREATE UNIQUE INDEX reports_unique_comment_per_reporter ON reports(reporter_id, target_comment_id) WHERE target_comment_id IS NOT NULL;
