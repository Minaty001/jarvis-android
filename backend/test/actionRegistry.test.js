import { ActionRegistry } from '../src/actions/actionRegistry.js';

describe('ActionRegistry completeness', () => {
  const expectedActions = [
    'open_app', 'tap', 'type', 'swipe', 'press_back', 'read_screen',
    'send_sms', 'make_call', 'open_url', 'media_control',
    'wifi', 'bluetooth', 'calendar', 'share'
  ];

  test('has all expected actions', () => {
    expectedActions.forEach(action => {
      expect(ActionRegistry[action]).toBeDefined();
      expect(ActionRegistry[action].name).toBe(action);
    });
  });

  test('each action has required fields', () => {
    expectedActions.forEach(action => {
      const entry = ActionRegistry[action];
      expect(typeof entry.risk).toBe('string');
      expect(typeof entry.requiresConfirmation).toBe('boolean');
      expect(Array.isArray(entry.requiredPermissions)).toBe(true);
      expect(typeof entry.supportsBackground).toBe('boolean');
      expect(typeof entry.supportsAutomation).toBe('boolean');
    });
  });

  test('risk levels are valid', () => {
    const validRisks = ['low', 'medium', 'high', 'forbidden'];
    expectedActions.forEach(action => {
      expect(validRisks).toContain(ActionRegistry[action].risk);
    });
  });

  test('send_sms and make_call are high risk with confirmation', () => {
    expect(ActionRegistry.send_sms.risk).toBe('high');
    expect(ActionRegistry.send_sms.requiresConfirmation).toBe(true);
    expect(ActionRegistry.make_call.risk).toBe('high');
    expect(ActionRegistry.make_call.requiresConfirmation).toBe(true);
  });

  test('low risk actions do not require confirmation', () => {
    ['open_app', 'tap', 'swipe', 'press_back', 'read_screen'].forEach(action => {
      expect(ActionRegistry[action].risk).toBe('low');
      expect(ActionRegistry[action].requiresConfirmation).toBe(false);
    });
  });

  test('accessibility actions require accessibility permission', () => {
    ['tap', 'type', 'swipe', 'press_back', 'read_screen'].forEach(action => {
      expect(ActionRegistry[action].requiredPermissions).toContain('accessibility');
    });
  });
});
