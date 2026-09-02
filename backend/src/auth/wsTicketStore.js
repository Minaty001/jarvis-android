import crypto from "node:crypto";

export class WsTicketStore {
  constructor() {
    this.tickets = new Map();
  }

  createTicket(deviceId, ttlMs = 60000) {
    const ticket = crypto.randomBytes(24).toString("base64url");
    const expiresAt = Date.now() + ttlMs;
    this.tickets.set(ticket, { deviceId, expiresAt, used: false });
    this.cleanup();
    return { ticket, expiresAt };
  }

  consumeTicket(ticket) {
    const entry = this.tickets.get(ticket);
    if (!entry) return null;
    this.tickets.delete(ticket);
    if (entry.used) return null;
    if (Date.now() > entry.expiresAt) return null;
    entry.used = true;
    return { deviceId: entry.deviceId };
  }

  cleanup() {
    const now = Date.now();
    for (const [key, entry] of this.tickets) {
      if (now > entry.expiresAt) this.tickets.delete(key);
    }
  }
}
