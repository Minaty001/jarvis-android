-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    preferences JSONB DEFAULT '{}'
);

-- Memories table with vector embeddings
CREATE TABLE IF NOT EXISTS memories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding VECTOR(768),
    memory_type TEXT CHECK (memory_type IN ('fact', 'skill', 'preference', 'conversation')),
    importance FLOAT DEFAULT 0.5,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Skills table with vector embeddings
CREATE TABLE IF NOT EXISTS skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    trigger_pattern TEXT,
    action_sequence JSONB NOT NULL,
    examples TEXT[] DEFAULT '{}',
    embedding VECTOR(768),
    usage_count INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_used TIMESTAMP WITH TIME ZONE
);

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    device_id TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_active TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    context JSONB DEFAULT '{}'
);

-- Reminders table
CREATE TABLE IF NOT EXISTS reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    remind_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_memories_user ON memories(user_id);
CREATE INDEX IF NOT EXISTS idx_memories_embedding ON memories
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_skills_user ON skills(user_id);
CREATE INDEX IF NOT EXISTS idx_skills_embedding ON skills
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);
CREATE INDEX IF NOT EXISTS idx_reminders_user_time ON reminders(user_id, remind_at);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);

-- Match memories function for vector similarity search
CREATE OR REPLACE FUNCTION match_memories(
    query_embedding VECTOR(768),
    match_threshold FLOAT,
    match_count INT,
    p_user_id UUID
)
RETURNS TABLE(
    id UUID,
    content TEXT,
    similarity FLOAT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        memories.id,
        memories.content,
        1 - (memories.embedding <=> query_embedding) AS similarity
    FROM memories
    WHERE memories.user_id = p_user_id
        AND 1 - (memories.embedding <=> query_embedding) > match_threshold
    ORDER BY similarity DESC
    LIMIT match_count;
END;
$$;

-- Match skills function for vector similarity search
CREATE OR REPLACE FUNCTION match_skills(
    query_embedding VECTOR(768),
    match_threshold FLOAT,
    match_count INT,
    p_user_id UUID
)
RETURNS TABLE(
    id UUID,
    name TEXT,
    trigger_pattern TEXT,
    action_sequence JSONB,
    similarity FLOAT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        skills.id,
        skills.name,
        skills.trigger_pattern,
        skills.action_sequence,
        1 - (skills.embedding <=> query_embedding) AS similarity
    FROM skills
    WHERE skills.user_id = p_user_id
        AND 1 - (skills.embedding <=> query_embedding) > match_threshold
    ORDER BY similarity DESC
    LIMIT match_count;
END;
$$;

-- Increment skill usage counter
CREATE OR REPLACE FUNCTION increment_skill_usage(p_skill_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE skills
    SET usage_count = usage_count + 1,
        last_used = NOW()
    WHERE id = p_skill_id;
END;
$$;
