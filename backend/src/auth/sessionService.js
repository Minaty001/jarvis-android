export class SessionService {
  constructor(supabase) {
    this.supabase = supabase;
  }

  async getSession(deviceId) {
    const { data, error } = await this.supabase
      .from("device_sessions")
      .select("*")
      .eq("device_id", deviceId)
      .is("revoked_at", null)
      .order("created_at", { ascending: false })
      .limit(1)
      .single();

    if (error) return null;
    return data;
  }

  async revokeAllSessions(deviceId) {
    const { error } = await this.supabase
      .from("device_sessions")
      .update({ revoked_at: new Date().toISOString() })
      .eq("device_id", deviceId);

    if (error) throw error;
  }

  async cleanupExpiredSessions() {
    const { error } = await this.supabase
      .from("device_sessions")
      .delete()
      .lt("refresh_expires_at", new Date().toISOString());

    if (error) throw error;
  }
}