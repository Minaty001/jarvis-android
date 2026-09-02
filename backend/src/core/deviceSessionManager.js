import { createClient } from '@supabase/supabase-js';
import { CONFIG } from '../config.js';
import crypto from 'node:crypto';

export class DeviceSessionManager {
  constructor() {
    this.supabase = null;
    this.available = false;
    this._init();
  }

  _init() {
    if (CONFIG.supabaseUrl && CONFIG.supabaseKey) {
      this.supabase = createClient(CONFIG.supabaseUrl, CONFIG.supabaseKey);
      this.available = true;
      console.log('Device session manager connected to Supabase');
    } else {
      console.warn('Supabase not configured. Device sessions are in-memory only.');
    }
  }

  hashToken(token) {
    return crypto.createHash('sha256').update(token).digest('hex');
  }

  async registerDevice({ deviceId, deviceName, deviceModel, osVersion }) {
    const accessToken = crypto.randomBytes(32).toString('base64url');
    const refreshToken = crypto.randomBytes(32).toString('base64url');
    const accessHash = this.hashToken(accessToken);
    const refreshHash = this.hashToken(refreshToken);
    const accessExpiresAt = new Date(Date.now() + 86400_000).toISOString();
    const refreshExpiresAt = new Date(Date.now() + 30 * 86400_000).toISOString();

    if (!this.available) {
      return { accessToken, refreshToken, expiresIn: 86400, deviceId, trusted: false };
    }

    const { error: upsertError } = await this.supabase
      .from('users')
      .upsert({ device_id: deviceId }, { onConflict: 'device_id' });
    if (upsertError) throw upsertError;

    const { data: user } = await this.supabase
      .from('users')
      .select('id')
      .eq('device_id', deviceId)
      .single();

    const { error: sessionError } = await this.supabase
      .from('device_sessions')
      .insert({
        device_id: deviceId,
        access_token_hash: accessHash,
        refresh_token_hash: refreshHash,
        access_expires_at: accessExpiresAt,
        refresh_expires_at: refreshExpiresAt,
        device_name: deviceName,
        device_model: deviceModel,
        os_version: osVersion,
      });
    if (sessionError) throw sessionError;

    await this.supabase
      .from('users')
      .update({ last_seen: new Date().toISOString() })
      .eq('device_id', deviceId);

    return { accessToken, refreshToken, expiresIn: 86400, deviceId, trusted: false };
  }

  async validateToken(deviceId, accessToken) {
    if (!this.available) return true;
    const hash = this.hashToken(accessToken);
    const { data, error } = await this.supabase
      .from('device_sessions')
      .select('id, access_expires_at, revoked_at')
      .eq('device_id', deviceId)
      .eq('access_token_hash', hash)
      .single();

    if (error || !data) return false;
    if (data.revoked_at) return false;
    if (new Date(data.access_expires_at) < new Date()) return false;

    await this.supabase
      .from('device_sessions')
      .update({ last_used_at: new Date().toISOString() })
      .eq('id', data.id);

    return true;
  }

  async refreshTokens(refreshToken) {
    const hash = this.hashToken(refreshToken);
    const { data: session, error } = await this.supabase
      .from('device_sessions')
      .select('id, device_id, refresh_expires_at, revoked_at')
      .eq('refresh_token_hash', hash)
      .single();

    if (error || !session) return null;
    if (session.revoked_at) return null;
    if (new Date(session.refresh_expires_at) < new Date()) return null;

    const newAccess = crypto.randomBytes(32).toString('base64url');
    const newRefresh = crypto.randomBytes(32).toString('base64url');
    const newAccessHash = this.hashToken(newAccess);
    const newRefreshHash = this.hashToken(newRefresh);
    const accessExpiresAt = new Date(Date.now() + 86400_000).toISOString();
    const refreshExpiresAt = new Date(Date.now() + 30 * 86400_000).toISOString();

    const { error: updateError } = await this.supabase
      .from('device_sessions')
      .update({
        access_token_hash: newAccessHash,
        refresh_token_hash: newRefreshHash,
        access_expires_at: accessExpiresAt,
        refresh_expires_at: refreshExpiresAt,
        last_used_at: new Date().toISOString(),
      })
      .eq('id', session.id);

    if (updateError) throw updateError;

    return {
      accessToken: newAccess,
      refreshToken: newRefresh,
      expiresIn: 86400,
      deviceId: session.device_id,
      trusted: false,
    };
  }

  async revokeSession(deviceId) {
    if (!this.available) return;
    await this.supabase
      .from('device_sessions')
      .update({ revoked_at: new Date().toISOString() })
      .eq('device_id', deviceId);
  }
}
