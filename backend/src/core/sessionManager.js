import { CONFIG } from '../config.js';

class Session {
  constructor(deviceId) {
    this.deviceId = deviceId;
    this.context = {};
    this.history = [];
    this.createdAt = Date.now();
    this.lastActive = Date.now();
  }

  addMessage(role, content) {
    this.history.push({ role, content, timestamp: Date.now() });
    if (this.history.length > 50) this.history = this.history.slice(-50);
    this.lastActive = Date.now();
  }

  getMessages() {
    return this.history.map(m => ({ role: m.role, content: m.content }));
  }

  isExpired() {
    return Date.now() - this.lastActive > CONFIG.sessionTimeoutMs;
  }
}

export class SessionManager {
  constructor() {
    this.sessions = new Map();
    this._startCleanup();
  }

  create(deviceId) {
    if (this.sessions.has(deviceId)) {
      return this.sessions.get(deviceId);
    }
    const session = new Session(deviceId);
    this.sessions.set(deviceId, session);
    return session;
  }

  get(deviceId) {
    return this.sessions.get(deviceId) || null;
  }

  remove(deviceId) {
    this.sessions.delete(deviceId);
  }

  _startCleanup() {
    const timer = setInterval(() => {
      for (const [id, session] of this.sessions) {
        if (session.isExpired()) {
          this.sessions.delete(id);
        }
      }
    }, 60000);
    timer.unref();
  }

  getStats() {
    return {
      activeSessions: this.sessions.size,
      sessions: Array.from(this.sessions.entries()).map(([id, s]) => ({
        deviceId: id,
        messages: s.history.length,
        lastActive: s.lastActive,
      })),
    };
  }
}
