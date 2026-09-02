#!/usr/bin/env node

import { config } from "dotenv";
config();

import crypto from "node:crypto";
import { createClient } from "@supabase/supabase-js";

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_KEY;

if (!supabaseUrl || !supabaseKey) {
  console.error("Error: SUPABASE_URL and SUPABASE_KEY must be set in .env");
  process.exit(1);
}

const supabase = createClient(supabaseUrl, supabaseKey);

const deviceId = process.argv[2];
const deviceName = process.argv[3] || "Android Device";

if (!deviceId) {
  console.log("Usage: node scripts/enroll-device.js <device_id> [device_name]");
  console.log("  device_id: unique identifier (UUID format recommended)");
  console.log("  device_name: human-readable name (optional)");
  console.log("");
  console.log("Example:");
  console.log("  node scripts/enroll-device.js abc123-def456 'My Phone'");
  process.exit(1);
}

const enrollmentSecret = crypto.randomBytes(16).toString("hex");

const { error: insertError } = await supabase
  .from("devices")
  .insert({
    device_id: deviceId,
    device_name: deviceName,
    device_model: "unknown",
    os_version: "unknown",
    enrollment_secret: enrollmentSecret,
    enrolled_at: new Date().toISOString(),
  });

if (insertError) {
  console.error("Error creating device:", insertError.message);
  process.exit(1);
}

console.log("Device enrolled successfully!");
console.log("");
console.log("  Device ID:    ", deviceId);
console.log("  Device Name:  ", deviceName);
console.log("  Pairing Code: ", enrollmentSecret);
console.log("");
console.log("Enter this pairing code in the JARVIS app Settings → Pair Device");
