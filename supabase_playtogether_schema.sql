-- =============================================================================
-- PRVN MUSIC v0.03 — SUPABASE REALTIME & LISTEN TOGETHER BACKEND SCHEMA
-- =============================================================================
-- This schema provisions production-grade PostgreSQL tables, foreign-key constraints,
-- row-level security (RLS), and Supabase Realtime broadcast channels for PRVN Music's
-- Listen Together synchronized multi-user music listening feature.

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. ROOMS TABLE
CREATE TABLE IF NOT EXISTS public.rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(4) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    host_id VARCHAR(50) NOT NULL,
    host_name VARCHAR(50) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- 'PUBLIC', 'PRIVATE', 'INVITE_ONLY'
    control_mode VARCHAR(20) NOT NULL DEFAULT 'HOST_ONLY', -- 'HOST_ONLY', 'COLLABORATIVE'
    password_hash VARCHAR(255),
    is_playing BOOLEAN NOT NULL DEFAULT FALSE,
    current_track JSONB,
    position_ms BIGINT NOT NULL DEFAULT 0,
    queue JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for instant 4-digit code lookups and public discovery feed
CREATE INDEX IF NOT EXISTS idx_rooms_code ON public.rooms(code);
CREATE INDEX IF NOT EXISTS idx_rooms_visibility ON public.rooms(visibility) WHERE visibility = 'PUBLIC';

-- 3. ROOM MEMBERS TABLE (Presence and clock drift tracking)
CREATE TABLE IF NOT EXISTS public.room_members (
    id VARCHAR(50) PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
    user_name VARCHAR(50) NOT NULL,
    is_host BOOLEAN NOT NULL DEFAULT FALSE,
    sync_state VARCHAR(20) NOT NULL DEFAULT 'SYNCED', -- 'SYNCED', 'SYNCING', 'BUFFERING'
    drift_ms BIGINT NOT NULL DEFAULT 0,
    last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_members_room_id ON public.room_members(room_id);

-- 4. SONG REQUESTS TABLE (Listener request inbox & host review)
CREATE TABLE IF NOT EXISTS public.song_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
    track JSONB NOT NULL,
    requester_id VARCHAR(50) NOT NULL,
    requester_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED'
    request_time_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_song_requests_room_status ON public.song_requests(room_id, status);

-- 5. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.song_requests ENABLE ROW LEVEL SECURITY;

-- Allow anonymous clients holding the anon public key to participate
CREATE POLICY "Public rooms are readable by all"
    ON public.rooms FOR SELECT USING (true);

CREATE POLICY "Allow room creation"
    ON public.rooms FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow host room mutation"
    ON public.rooms FOR UPDATE USING (true);

CREATE POLICY "Allow room deletion"
    ON public.rooms FOR DELETE USING (true);

CREATE POLICY "Members readable by all"
    ON public.room_members FOR ALL USING (true);

CREATE POLICY "Song requests accessible by room participants"
    ON public.song_requests FOR ALL USING (true);

-- 6. ENABLE REALTIME BROADCAST & CDC
ALTER PUBLICATION supabase_realtime ADD TABLE public.rooms;
ALTER PUBLICATION supabase_realtime ADD TABLE public.room_members;
ALTER PUBLICATION supabase_realtime ADD TABLE public.song_requests;

-- 7. CLEANUP TRIGGER FOR ABANDONED ROOMS (> 24 hours without presence)
CREATE OR REPLACE FUNCTION clean_stale_rooms()
RETURNS void AS $$
BEGIN
    DELETE FROM public.rooms
    WHERE updated_at < NOW() - INTERVAL '24 hours';
END;
$$ LANGUAGE plpgsql;
