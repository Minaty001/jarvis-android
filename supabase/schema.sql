-- JARVIS Supabase Schema
-- Run this in Supabase SQL Editor

-- Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- ==================== DEVICES ====================
CREATE TABLE IF NOT EXISTS devices (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  device_id TEXT UNIQUE NOT NULL,
  device_name TEXT,
  device_model TEXT,
  os_version TEXT,
  enrollment_secret TEXT,
  enrolled_at TIMESTAMPTZ DEFAULT NOW(),
  last_seen TIMESTAMPTZ DEFAULT NOW()
);

-- ==================== DEVICE SESSIONS ====================
CREATE TABLE IF NOT EXISTS device_sessions (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  device_id TEXT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
  access_token_hash TEXT NOT NULL,
  refresh_token_hash TEXT NOT NULL,
  access_expires_at TIMESTAMPTZ NOT NULL,
  refresh_expires_at TIMESTAMPTZ NOT NULL,
  device_name TEXT,
  device_model TEXT,
  os_version TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  last_used_at TIMESTAMPTZ DEFAULT NOW(),
  revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_sessions_device ON device_sessions(device_id);
CREATE INDEX idx_sessions_access_hash ON device_sessions(access_token_hash);
CREATE INDEX idx_sessions_refresh_hash ON device_sessions(refresh_token_hash);

-- ==================== USERS ====================
CREATE TABLE IF NOT EXISTS users (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  device_id TEXT UNIQUE NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  last_seen TIMESTAMPTZ DEFAULT NOW()
);

-- ==================== MEMORIES ====================
CREATE TABLE IF NOT EXISTS memories (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  memory_type TEXT DEFAULT 'conversation',
  importance FLOAT DEFAULT 0.5,
  embedding VECTOR(768),
  timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_memories_user ON memories(user_id);

-- ==================== SKILLS ====================
CREATE TABLE IF NOT EXISTS skills (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  trigger_pattern TEXT NOT NULL,
  action_sequence JSONB NOT NULL DEFAULT '[]',
  examples TEXT[] DEFAULT '{}',
  embedding VECTOR(768),
  usage_count INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_skills_user ON skills(user_id);

-- ==================== SESSIONS ====================
CREATE TABLE IF NOT EXISTS sessions (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  device_id TEXT NOT NULL,
  messages JSONB DEFAULT '[]',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours')
);

-- ==================== REMINDERS ====================
CREATE TABLE IF NOT EXISTS reminders (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  message TEXT,
  remind_at TIMESTAMPTZ NOT NULL,
  recurrence TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_reminders_user ON reminders(user_id);

-- ==================== FUNCTIONS ====================

-- Match memories by cosine similarity
CREATE OR REPLACE FUNCTION match_memories(
  query_embedding VECTOR(768),
  match_threshold FLOAT,
  match_count INT,
  p_user_id UUID
)
RETURNS TABLE (
  id UUID,
  content TEXT,
  memory_type TEXT,
  importance FLOAT,
  similarity FLOAT,
  timestamp TIMESTAMPTZ
)
LANGUAGE plpgsql
AS $$
BEGIN
  RETURN QUERY
  SELECT
    m.id,
    m.content,
    m.memory_type,
    m.importance,
    1 - (m.embedding <=> query_embedding) AS similarity,
    m.timestamp
  FROM memories m
  WHERE m.user_id = p_user_id
    AND m.embedding IS NOT NULL
    AND 1 - (m.embedding <=> query_embedding) > match_threshold
  ORDER BY m.embedding <=> query_embedding
  LIMIT match_count;
END;
$$;

-- Match skills by cosine similarity
CREATE OR REPLACE FUNCTION match_skills(
  query_embedding VECTOR(768),
  match_threshold FLOAT,
  match_count INT,
  p_user_id UUID
)
RETURNS TABLE (
  id UUID,
  name TEXT,
  trigger_pattern TEXT,
  action_sequence JSONB,
  examples TEXT[],
  usage_count INT,
  similarity FLOAT
)
LANGUAGE plpgsql
AS $$
BEGIN
  RETURN QUERY
  SELECT
    s.id,
    s.name,
    s.trigger_pattern,
    s.action_sequence,
    s.examples,
    s.usage_count,
    1 - (s.embedding <=> query_embedding) AS similarity
  FROM skills s
  WHERE s.user_id = p_user_id
    AND s.embedding IS NOT NULL
    AND 1 - (s.embedding <=> query_embedding) > match_threshold
  ORDER BY s.embedding <=> query_embedding
  LIMIT match_count;
END;
$$;

-- Increment skill usage
CREATE OR REPLACE FUNCTION increment_skill_usage(p_skill_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  UPDATE skills SET usage_count = usage_count + 1, updated_at = NOW() WHERE id = p_skill_id;
END;
$$;

-- ==================== ROW LEVEL SECURITY ====================
ALTER TABLE memories ENABLE ROW LEVEL SECURITY;
ALTER TABLE skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE reminders ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own memories" ON memories FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own memories" ON memories FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own memories" ON memories FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can view own skills" ON skills FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own skills" ON skills FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own skills" ON skills FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can view own reminders" ON reminders FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own reminders" ON reminders FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own reminders" ON reminders FOR DELETE USING (auth.uid() = user_id);