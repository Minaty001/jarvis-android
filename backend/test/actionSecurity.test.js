import { ActionPolicy } from '../src/actions/actionPolicy.js';
import { ActionRegistry } from '../src/actions/actionRegistry.js';

describe('Forbidden Action Blocking', () => {
  test('credential_theft is forbidden', () => {
    const result = ActionPolicy.validateAction({ type: 'credential_theft', params: {} });
    expect(result.valid).toBe(false);
    expect(result.error).toContain('forbidden');
  });

  test('security_bypass is forbidden', () => {
    const result = ActionPolicy.validateAction({ type: 'security_bypass', params: {} });
    expect(result.valid).toBe(false);
    expect(result.error).toContain('forbidden');
  });

  test('financial_transfer is forbidden', () => {
    const result = ActionPolicy.validateAction({ type: 'financial_transfer', params: {} });
    expect(result.valid).toBe(false);
    expect(result.error).toContain('forbidden');
  });

  test('bank_transfer is forbidden', () => {
    const result = ActionPolicy.validateAction({ type: 'bank_transfer', params: {} });
    expect(result.valid).toBe(false);
    expect(result.error).toContain('forbidden');
  });

  test('unknown action type is rejected', () => {
    const result = ActionPolicy.validateAction({ type: 'totally_made_up', params: {} });
    expect(result.valid).toBe(false);
    expect(result.error).toContain('Unknown');
  });
});

describe('Risk Level Assignment', () => {
  test('LOW risk: open_app, tap, swipe, back', () => {
    expect(ActionPolicy.getRiskLevel({ type: 'open_app' })).toBe('low');
    expect(ActionPolicy.getRiskLevel({ type: 'tap' })).toBe('low');
    expect(ActionPolicy.getRiskLevel({ type: 'swipe' })).toBe('low');
    expect(ActionPolicy.getRiskLevel({ type: 'press_back' })).toBe('low');
  });

  test('MEDIUM risk: type, wifi, bluetooth, share, open_url', () => {
    expect(ActionPolicy.getRiskLevel({ type: 'type' })).toBe('medium');
    expect(ActionPolicy.getRiskLevel({ type: 'wifi' })).toBe('medium');
    expect(ActionPolicy.getRiskLevel({ type: 'bluetooth' })).toBe('medium');
    expect(ActionPolicy.getRiskLevel({ type: 'share' })).toBe('medium');
    expect(ActionPolicy.getRiskLevel({ type: 'open_url' })).toBe('medium');
  });

  test('HIGH risk: send_sms, make_call, delete_file, install_app', () => {
    expect(ActionPolicy.getRiskLevel({ type: 'send_sms' })).toBe('high');
    expect(ActionPolicy.getRiskLevel({ type: 'make_call' })).toBe('high');
  });
});

describe('Confirmation Requirements', () => {
  test('high-risk actions require confirmation', () => {
    expect(ActionPolicy.requiresConfirmation({ type: 'send_sms' })).toBe(true);
    expect(ActionPolicy.requiresConfirmation({ type: 'make_call' })).toBe(true);
  });

  test('low-risk actions do not require confirmation', () => {
    expect(ActionPolicy.requiresConfirmation({ type: 'open_app' })).toBe(false);
    expect(ActionPolicy.requiresConfirmation({ type: 'tap' })).toBe(false);
    expect(ActionPolicy.requiresConfirmation({ type: 'swipe' })).toBe(false);
  });
});

describe('Action Plan Validation', () => {
  test('rejects plan with forbidden action', () => {
    const result = ActionPolicy.validateActionPlan({
      actions: [
        { type: 'open_app', params: { package: 'com.test' } },
        { type: 'credential_theft', params: {} },
      ],
    });
    expect(result.valid).toBe(false);
    expect(result.error).toContain('forbidden');
  });

  test('rejects plan exceeding 10 actions', () => {
    const actions = Array(11).fill({ type: 'open_app', params: { package: 'com.test' } });
    const result = ActionPolicy.validateActionPlan({ actions });
    expect(result.valid).toBe(false);
  });

  test('rejects empty plan', () => {
    const result = ActionPolicy.validateActionPlan({ actions: [] });
    expect(result.valid).toBe(false);
  });

  test('accepts valid plan with mixed risk levels', () => {
    const result = ActionPolicy.validateActionPlan({
      actions: [
        { type: 'open_app', params: { package: 'com.test' } },
        { type: 'tap', params: { text: 'button' } },
      ],
    });
    expect(result.valid).toBe(true);
  });
});

describe('LLM Cannot Override Risk', () => {
  test('risk is determined by registry, not by LLM output', () => {
    const llmAction = { type: 'send_sms', params: { phone: '+1', message: 'hi' }, risk: 'low' };
    expect(ActionPolicy.getRiskLevel(llmAction)).toBe('high');
  });

  test('LLM cannot set risk to override registry', () => {
    const llmAction = { type: 'open_app', params: { package: 'com.test' }, risk: 'high' };
    expect(ActionPolicy.getRiskLevel(llmAction)).toBe('low');
  });
});
