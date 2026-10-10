-- "Listen again" is limited per question (GameConstants.MAX_LISTEN_AGAIN); the server counts the restarts.
ALTER TABLE session_questions ADD COLUMN IF NOT EXISTS timer_restarts SMALLINT NOT NULL DEFAULT 0;
