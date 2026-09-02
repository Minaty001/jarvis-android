export class ConfirmationManager {
  constructor() {
    this.pendingConfirmations = new Map();
    this.timeout = 20000;
  }

  createConfirmationRequest(actionType, riskLevel, params) {
    const requestId = `conf_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    const request = {
      id: requestId,
      actionType,
      riskLevel,
      params,
      createdAt: Date.now(),
      status: "pending"
    };

    this.pendingConfirmations.set(requestId, request);
    
    setTimeout(() => {
      const req = this.pendingConfirmations.get(requestId);
      if (req && req.status === "pending") {
        req.status = "timeout";
        this.pendingConfirmations.delete(requestId);
      }
    }, this.timeout);

    return request;
  }

  confirm(requestId, approved) {
    const request = this.pendingConfirmations.get(requestId);
    if (!request) {
      return { success: false, error: "Request not found" };
    }

    if (request.status !== "pending") {
      return { success: false, error: "Request already processed" };
    }

    request.status = approved ? "approved" : "denied";
    request.processedAt = Date.now();
    this.pendingConfirmations.delete(requestId);

    return { success: true, status: request.status };
  }

  getPendingRequest(requestId) {
    return this.pendingConfirmations.get(requestId) || null;
  }

  cleanupExpiredRequests() {
    const now = Date.now();
    for (const [id, request] of this.pendingConfirmations.entries()) {
      if (now - request.createdAt > this.timeout) {
        this.pendingConfirmations.delete(id);
      }
    }
  }
}