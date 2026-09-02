export const ActionRegistry = {
  open_app: {
    name: "open_app",
    risk: "low",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: true,
    supportsAutomation: true
  },
  tap: {
    name: "tap",
    risk: "low",
    requiresConfirmation: false,
    requiredPermissions: ["accessibility"],
    supportsBackground: false,
    supportsAutomation: true
  },
  type: {
    name: "type",
    risk: "medium",
    requiresConfirmation: false,
    requiredPermissions: ["accessibility"],
    supportsBackground: false,
    supportsAutomation: true
  },
  swipe: {
    name: "swipe",
    risk: "low",
    requiresConfirmation: false,
    requiredPermissions: ["accessibility"],
    supportsBackground: false,
    supportsAutomation: true
  },
  press_back: {
    name: "press_back",
    risk: "low",
    requiresConfirmation: false,
    requiredPermissions: ["accessibility"],
    supportsBackground: false,
    supportsAutomation: true
  },
  read_screen: {
    name: "read_screen",
    risk: "low",
    requiresConfirmation: false,
    requiredPermissions: ["accessibility"],
    supportsBackground: false,
    supportsAutomation: true
  },
  send_sms: {
    name: "send_sms",
    risk: "high",
    requiresConfirmation: true,
    requiredPermissions: ["sms"],
    supportsBackground: true,
    supportsAutomation: false
  },
  make_call: {
    name: "make_call",
    risk: "high",
    requiresConfirmation: true,
    requiredPermissions: ["phone"],
    supportsBackground: false,
    supportsAutomation: false
  },
  open_url: {
    name: "open_url",
    risk: "medium",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: true,
    supportsAutomation: true
  },
  media_control: {
    name: "media_control",
    risk: "medium",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: true,
    supportsAutomation: true
  },
  wifi: {
    name: "wifi",
    risk: "medium",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: true,
    supportsAutomation: true
  },
  bluetooth: {
    name: "bluetooth",
    risk: "medium",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: true,
    supportsAutomation: true
  },
  calendar: {
    name: "calendar",
    risk: "low",
    requiresConfirmation: false,
    requiredPermissions: ["calendar"],
    supportsBackground: true,
    supportsAutomation: true
  },
  share: {
    name: "share",
    risk: "medium",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: false,
    supportsAutomation: true
  },
  credential_theft: {
    name: "credential_theft",
    risk: "forbidden",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: false,
    supportsAutomation: false
  },
  security_bypass: {
    name: "security_bypass",
    risk: "forbidden",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: false,
    supportsAutomation: false
  },
  financial_transfer: {
    name: "financial_transfer",
    risk: "forbidden",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: false,
    supportsAutomation: false
  },
  bank_transfer: {
    name: "bank_transfer",
    risk: "forbidden",
    requiresConfirmation: false,
    requiredPermissions: [],
    supportsBackground: false,
    supportsAutomation: false
  }
};