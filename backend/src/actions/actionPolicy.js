import { ActionRegistry } from "./actionRegistry.js";

export class ActionPolicy {
  static validateAction(action) {
    const registry = ActionRegistry[action.type];
    if (!registry) {
      return { valid: false, error: `Unknown action type: ${action.type}` };
    }

    if (registry.risk === "forbidden") {
      return { valid: false, error: `Action type ${action.type} is forbidden` };
    }

    return { valid: true, policy: registry };
  }

  static requiresConfirmation(action) {
    const registry = ActionRegistry[action.type];
    return registry?.requiresConfirmation || false;
  }

  static getRiskLevel(action) {
    const registry = ActionRegistry[action.type];
    return registry?.risk || "medium";
  }

  static validateActionPlan(actionPlan) {
    if (!actionPlan.actions || !Array.isArray(actionPlan.actions)) {
      return { valid: false, error: "Action plan must contain actions array" };
    }

    if (actionPlan.actions.length === 0) {
      return { valid: false, error: "Action plan must have at least one action" };
    }

    if (actionPlan.actions.length > 10) {
      return { valid: false, error: "Action plan cannot have more than 10 actions" };
    }

    for (const action of actionPlan.actions) {
      const validation = this.validateAction(action);
      if (!validation.valid) {
        return validation;
      }
    }

    return { valid: true };
  }
}