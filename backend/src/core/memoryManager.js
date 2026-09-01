import { createClient } from '@supabase/supabase-js';
import { CONFIG } from '../config.js';

export class MemoryManager {
  constructor() {
    this.supabase = null;
    this.available = false;
    this._init();
  }

  _init() {
    if (CONFIG.supabaseUrl && CONFIG.supabaseKey) {
      this.supabase = createClient(CONFIG.supabaseUrl, CONFIG.supabaseKey);
      this.available = true;
      console.log('Memory manager connected to Supabase');
    } else {
      console.warn('Supabase not configured. Memory features disabled.');
    }
  }

  async store(userId, content, memoryType = 'conversation', importance = 0.5) {
    if (!this.available) return null;
    const dbUserId = await this._getUserId(userId);

    const { data, error } = await this.supabase
      .from('memories')
      .insert({
        user_id: dbUserId,
        content,
        memory_type: memoryType,
        importance,
        embedding: await this._getEmbedding(content),
      })
      .select()
      .single();

    if (error) throw error;
    return data;
  }

  async deleteMemory(memoryId) {
    if (!this.available) return false;
    const { error } = await this.supabase
      .from('memories')
      .delete()
      .eq('id', memoryId);
    if (error) throw error;
    return true;
  }

  async search(userId, query, limit = CONFIG.memorySearchLimit) {
    if (!this.available) return [];

    const dbUserId = await this._getUserId(userId);
    const embedding = await this._getEmbedding(query);

    const { data, error } = await this.supabase.rpc('match_memories', {
      query_embedding: embedding,
      match_threshold: CONFIG.memorySimilarityThreshold,
      match_count: limit,
      p_user_id: dbUserId,
    });

    if (error) {
      console.error('Memory search error:', error.message);
      return [];
    }
    return data || [];
  }

  async getRecentMemories(userId, limit = 10) {
    if (!this.available) return [];
    const dbUserId = await this._getUserId(userId);

    const { data, error } = await this.supabase
      .from('memories')
      .select('id, content, memory_type, importance, timestamp')
      .eq('user_id', dbUserId)
      .order('timestamp', { ascending: false })
      .limit(limit);

    if (error) return [];
    return data || [];
  }

  async getStats(userId) {
    if (!this.available) return { totalMemories: 0, totalSkills: 0, lastSync: null };

    try {
      const dbUserId = await this._getUserId(userId);
      const { count: memCount } = await this.supabase
        .from('memories')
        .select('*', { count: 'exact', head: true })
        .eq('user_id', dbUserId);

      const { count: skillCount } = await this.supabase
        .from('skills')
        .select('*', { count: 'exact', head: true })
        .eq('user_id', dbUserId);

      return {
        totalMemories: memCount || 0,
        totalSkills: skillCount || 0,
        lastSync: new Date().toISOString(),
      };
    } catch (err) {
      console.error('Stats error:', err.message);
      return { totalMemories: 0, totalSkills: 0, lastSync: null };
    }
  }

  // ==================== SKILLS ====================

  async storeSkill(userId, name, triggerPattern, actionSequence, examples = []) {
    if (!this.available) return null;

    const dbUserId = await this._getUserId(userId);
    const embedding = await this._getEmbedding(`${name} ${triggerPattern} ${examples.join(' ')}`);

    const { data, error } = await this.supabase
      .from('skills')
      .insert({
        user_id: dbUserId,
        name,
        trigger_pattern: triggerPattern,
        action_sequence: actionSequence,
        examples,
        embedding,
      })
      .select()
      .single();

    if (error) throw error;
    return data;
  }

  async matchSkill(userId, command) {
    if (!this.available) return null;
    const dbUserId = await this._getUserId(userId);

    // Tier 1: Exact substring match on examples (fast path)
    const { data: allSkills, error: fetchError } = await this.supabase
      .from('skills')
      .select('*')
      .eq('user_id', dbUserId);

    if (!fetchError && allSkills) {
      for (const skill of allSkills) {
        if (skill.examples && skill.examples.some(ex =>
          command.toLowerCase().includes(ex.toLowerCase())
        )) {
          await this._incrementSkillUsage(skill.id);
          return { ...skill, matchType: 'exact' };
        }
      }
    }

    // Tier 2: Semantic similarity search via pgvector
    try {
      const embedding = await this._getEmbedding(command);
      const { data: similar, error: matchError } = await this.supabase.rpc('match_skills', {
        query_embedding: embedding,
        match_threshold: 0.65,
        match_count: 3,
        p_user_id: dbUserId,
      });

      if (!matchError && similar && similar.length > 0) {
        await this._incrementSkillUsage(similar[0].id);
        return { ...similar[0], matchType: 'semantic' };
      }
    } catch (err) {
      console.error('Semantic skill match failed:', err.message);
    }

    // Tier 3: No match → caller falls through to LLM
    return null;
  }

  async listSkills(userId) {
    if (!this.available) return [];
    const dbUserId = await this._getUserId(userId);

    const { data, error } = await this.supabase
      .from('skills')
      .select('*')
      .eq('user_id', dbUserId)
      .order('usage_count', { ascending: false });

    if (error) return [];
    return data || [];
  }

  async deleteSkill(skillId) {
    if (!this.available) return false;
    const { error } = await this.supabase
      .from('skills')
      .delete()
      .eq('id', skillId);
    if (error) throw error;
    return true;
  }

  async _incrementSkillUsage(skillId) {
    if (!this.available) return;
    try {
      await this.supabase.rpc('increment_skill_usage', { p_skill_id: skillId });
    } catch (err) {
      console.error('Skill usage increment failed:', err.message);
    }
  }

  async _getUserId(deviceId) {
    const { data, error } = await this.supabase
      .from('users')
      .upsert({ device_id: deviceId }, { onConflict: 'device_id' })
      .select('id')
      .single();

    if (error) throw error;
    return data.id;
  }

  // ==================== EMBEDDINGS ====================

  async _getEmbedding(text) {
    if (CONFIG.nvidiaNimApiKey) {
      try {
        const response = await fetch(
          'https://integrate.api.nvidia.com/v1/embeddings',
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${CONFIG.nvidiaNimApiKey}`,
            },
            body: JSON.stringify({
              model: 'nvidia/nv-embedqa-e5-v5',
              input: text,
              input_type: 'query',
              encoding_format: 'float',
            }),
          }
        );
        const data = await response.json();
        return data.data[0].embedding;
      } catch (err) {
        console.error('Embedding API failed:', err.message);
      }
    }

    return this._dummyEmbedding(text);
  }

  _dummyEmbedding(text) {
    const hash = Array.from(text).reduce((acc, c) => ((acc << 5) - acc + c.charCodeAt(0)) | 0, 0);
    const embedding = new Array(768).fill(0).map((_, i) => {
      const seed = (hash + i * 31) & 0x7fffffff;
      return ((seed / 0x7fffffff) * 2 - 1) * 0.1;
    });
    const norm = Math.sqrt(embedding.reduce((s, v) => s + v * v, 0));
    return embedding.map(v => v / norm);
  }

  getStatus() {
    return {
      available: this.available,
      provider: CONFIG.nvidiaNimApiKey ? 'nvidia-nim' : 'dummy',
    };
  }
}
