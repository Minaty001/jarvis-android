import crypto from "node:crypto";

export class EnrollmentService {
  constructor(supabase) {
    this.supabase = supabase;
  }

  async enrollDevice(deviceId, deviceName, deviceModel, osVersion) {
    const existingDevice = await this.getDevice(deviceId);
    if (existingDevice) {
      return { success: false, error: "Device already enrolled" };
    }

    const enrollmentSecret = crypto.randomBytes(16).toString("hex");
    const { error } = await this.supabase
      .from("devices")
      .insert({
        device_id: deviceId,
        device_name: deviceName,
        device_model: deviceModel,
        os_version: osVersion,
        enrollment_secret: enrollmentSecret,
        enrolled_at: new Date().toISOString()
      });

    if (error) throw error;

    return { success: true, enrollmentSecret };
  }

  async getDevice(deviceId) {
    const { data, error } = await this.supabase
      .from("devices")
      .select("*")
      .eq("device_id", deviceId)
      .single();

    if (error) return null;
    return data;
  }

  async verifyEnrollment(deviceId, enrollmentSecret) {
    const { data, error } = await this.supabase
      .from("devices")
      .select("device_id")
      .eq("device_id", deviceId)
      .eq("enrollment_secret", enrollmentSecret)
      .single();

    if (error || !data) return false;
    return true;
  }
}