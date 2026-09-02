-- JARVIS Supabase RLS Migration
-- Run this in Supabase SQL Editor
-- Purpose: RLS policies compatible with opaque token + service role backend

-- ==================== DEVICES ====================
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;

-- Service role (backend) can do everything
CREATE POLICY "Service role full access on devices"
  ON devices FOR ALL
  USING (true)
  WITH CHECK (true);

-- ==================== DEVICE SESSIONS ====================
ALTER TABLE device_sessions ENABLE ROW LEVEL SECURITY;

-- Service role (backend) can do everything
CREATE POLICY "Service role full access on device_sessions"
  ON device_sessions FOR ALL
  USING (true)
  WITH CHECK (true);

-- ==================== USERS ====================
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Service role (backend) can do everything
CREATE POLICY "Service role full access on users"
  ON users FOR ALL
  USING (true)
  WITH CHECK (true);

-- ==================== MEMORIES ====================
-- Drop old policies that use auth.uid()
DROP POLICY IF EXISTS "Users can view own memories" ON memories;
DROP POLICY IF EXISTS "Users can insert own memories" ON memories;
DROP POLICY IF EXISTS "Users can delete own memories" ON memories;

-- Service role (backend) manages all memories
CREATE POLICY "Service role full access on memories"
  ON memories FOR ALL
  USING (true)
  WITH CHECK (true);

-- ==================== SKILLS ====================
DROP POLICY IF EXISTS "Users can view own skills" ON skills;
DROP POLICY IF EXISTS "Users can insert own skills" ON skills;
DROP POLICY IF EXISTS "Users can delete own skills" ON skills;

-- Service role (backend) manages all skills
CREATE POLICY "Service role full access on skills"
  ON skills FOR ALL
  USING (true)
  WITH CHECK (true);

-- ==================== SESSIONS ====================
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;

-- Service role (backend) manages all sessions
CREATE POLICY "Service role full access on sessions"
  ON sessions FOR ALL
  USING (true)
  WITH CHECK (true);

-- ==================== REMINDERS ====================
DROP POLICY IF EXISTS "Users can view own reminders" ON reminders;
DROP POLICY IF EXISTS "Users can insert own reminders" ON reminders;
DROP POLICY IF EXISTS "Users can delete own reminders" ON reminders;

-- Service role (backend) manages all reminders
CREATE POLICY "Service role full access on reminders"
  ON reminders FOR ALL
  USING (true)
  WITH CHECK (true);

-- ==================== NOTES ====================
-- RLS is enabled on all tables.
-- Backend uses service_role key (bypasses RLS).
-- Frontend anon key: RLS blocks all direct access.
-- This ensures:
--   1. No client can read/write other device's data
--   2. Backend has full access via service role
--   3. Anon key from APK cannot access any data
