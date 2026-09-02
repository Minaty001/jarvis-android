import { ActionSchema, ActionPlanSchema } from '../src/actions/actionSchemas.js';
import { ActionPolicy } from '../src/actions/actionPolicy.js';
import { ActionRegistry } from '../src/actions/actionRegistry.js';

describe('ActionSchema', () => {
  test('valid open_app action', () => {
    const result = ActionSchema.safeParse({ type: 'open_app', params: { package: 'com.test' } });
    expect(result.success).toBe(true);
  });

  test('valid send_sms action', () => {
    const result = ActionSchema.safeParse({ type: 'send_sms', params: { phone: '+1234', message: 'hi' } });
    expect(result.success).toBe(true);
  });

  test('rejects unknown action type', () => {
    const result = ActionSchema.safeParse({ type: 'unknown_action', params: {} });
    expect(result.success).toBe(false);
  });

  test('rejects send_sms without phone', () => {
    const result = ActionSchema.safeParse({ type: 'send_sms', params: { message: 'hi' } });
    expect(result.success).toBe(false);
  });
});

describe('ActionPolicy', () => {
  test('validates known action', () => {
    const result = ActionPolicy.validateAction({ type: 'open_app', params: { package: 'com.test' } });
    expect(result.valid).toBe(true);
  });

  test('rejects unknown action', () => {
    const result = ActionPolicy.validateAction({ type: 'nonexistent', params: {} });
    expect(result.valid).toBe(false);
  });

  test('identifies high-risk actions', () => {
    expect(ActionPolicy.getRiskLevel({ type: 'send_sms' })).toBe('high');
    expect(ActionPolicy.getRiskLevel({ type: 'make_call' })).toBe('high');
  });

  test('identifies low-risk actions', () => {
    expect(ActionPolicy.getRiskLevel({ type: 'open_app' })).toBe('low');
    expect(ActionPolicy.getRiskLevel({ type: 'tap' })).toBe('low');
  });

  test('validates action plan', () => {
    const result = ActionPolicy.validateActionPlan({
      actions: [{ type: 'open_app', params: { package: 'com.test' } }]
    });
    expect(result.valid).toBe(true);
  });

  test('rejects empty action plan', () => {
    const result = ActionPolicy.validateActionPlan({ actions: [] });
    expect(result.valid).toBe(false);
  });

  test('rejects plan with >10 actions', () => {
    const actions = Array(11).fill({ type: 'open_app', params: { package: 'com.test' } });
    const result = ActionPolicy.validateActionPlan({ actions });
    expect(result.valid).toBe(false);
  });
});

describe('ActionRegistry', () => {
  test('has all expected action types', () => {
    const expected = ['open_app', 'tap', 'type', 'swipe', 'press_back', 'read_screen',
      'send_sms', 'make_call', 'open_url', 'media_control', 'wifi', 'bluetooth', 'calendar', 'share'];
    expected.forEach(type => {
      expect(ActionRegistry[type]).toBeDefined();
    });
  });

  test('high-risk actions require confirmation', () => {
    expect(ActionRegistry.send_sms.requiresConfirmation).toBe(true);
    expect(ActionRegistry.make_call.requiresConfirmation).toBe(true);
  });

  test('low-risk actions do not require confirmation', () => {
    expect(ActionRegistry.open_app.requiresConfirmation).toBe(false);
    expect(ActionRegistry.tap.requiresConfirmation).toBe(false);
  });
});
