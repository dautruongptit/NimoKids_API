-- "Listen again" restarts the countdown of the question the child is on. The server stores WHEN, so the deadline stays
-- decided by the server (the client countdown is only a visual aid).
ALTER TABLE session_questions ADD COLUMN IF NOT EXISTS timer_restarted_at TIMESTAMPTZ;
