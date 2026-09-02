import crypto from "node:crypto";

export function generateToken() {
  return crypto.randomBytes(32).toString("base64url");
}

export function hashToken(token) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

export class TokenService {
  constructor(supabase) {
    this.supabase = supabase;
  }

  async createSession(deviceId, deviceName, deviceModel, osVersion) {
    const accessToken = generateToken();
    const refreshToken = generateToken();
    const accessHash = hashToken(accessToken);
    const refreshHash = hashToken(refreshToken);
    const accessExpiresIn = 86400;
    const refreshExpiresIn = accessExpiresIn * 30;
    const accessExpiresAt = new Date(Date.now() + accessExpiresIn * 1000).toISOString();
    const refreshExpiresAt = new Date(Date.now() + refreshExpiresIn * 1000).toISOString();

    const { error: upsertError } = await this.supabase
      .from("devices")
      .upsert({ device_id: deviceId }, { onConflict: "device_id" });

    if (upsertError) throw upsertError;

    const { error: sessionError } = await this.supabase
      .from("device_sessions")
      .insert({
        device_id: deviceId,
        access_token_hash: accessHash,
        refresh_token_hash: refreshHash,
        access_expires_at: accessExpiresAt,
        refresh_expires_at: refreshExpiresAt,
        device_name: deviceName,
        device_model: deviceModel,
        os_version: osVersion
      });

    if (sessionError) throw sessionError;

    await this.supabase
      .from("devices")
      .update({ last_seen: new Date().toISOString() })
      .eq("device_id", deviceId);

    return {
      accessToken,
      refreshToken,
      expiresIn: accessExpiresIn,
      deviceId,
      trusted: false
    };
  }

  async validateToken(deviceId, accessToken) {
    const accessHash = hashToken(accessToken);
    const { data, error } = await this.supabase
      .from("device_sessions")
      .select("id, access_expires_at, revoked_at")
      .eq("device_id", deviceId)
      .eq("access_token_hash", accessHash)
      .single();

    if (error || !data) return false;
    if (data.revoked_at) return false;
    if (new Date(data.access_expires_at) < new Date()) return false;

    await this.supabase
      .from("device_sessions")
      .update({ last_used_at: new Date().toISOString() })
      .eq("id", data.id);

    return true;
  }

  async refreshTokens(refreshToken) {
    const refreshHash = hashToken(refreshToken);
    const { data: session, error } = await this.supabase
      .from("device_sessions")
      .select("id, device_id, refresh_expires_at, revoked_at")
      .eq("refresh_token_hash", refreshHash)
      .single();

    if (error || !session) return null;
    if (session.revoked_at) return null;
    if (new Date(session.refresh_expires_at) < new Date()) return null;

    const newAccess = generateToken();
    const newRefresh = generateToken();
    const newAccessHash = hashToken(newAccess);
    const newRefreshHash = hashToken(newRefresh);
    const accessExpiresIn = 86400;
    const refreshExpiresIn = accessExpiresIn * 30;
    const accessExpiresAt = new Date(Date.now() + accessExpiresIn * 1000).toISOString();
    const refreshExpiresAt = new Date(Date.now() + refreshExpiresIn * 1000).toISOString();

    const { error: updateError } = await this.supabase
      .from("device_sessions")
      .update({
        access_token_hash: newAccessHash,
        refresh_token_hash: newRefreshHash,
        access_expires_at: accessExpiresAt,
        refresh_expires_at: refreshExpiresAt,
        last_used_at: new Date().toISOString()
      })
      .eq("id", session.id);

    if (updateError) throw updateError;

    return {
      accessToken: newAccess,
      refreshToken: newRefresh,
      expiresIn: accessExpiresIn,
      deviceId: session.device_id,
      trusted: false
    };
  }
}