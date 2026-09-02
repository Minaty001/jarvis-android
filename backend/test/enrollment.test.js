import { EnrollmentService } from '../src/auth/enrollmentService.js';

function createMockSupabase(data = null, error = null) {
  let callCount = 0;
  return {
    from: function() { return this; },
    select: function() { return this; },
    insert: function() { return this; },
    eq: function() { return this; },
    single: function() {
      callCount++;
      return { data, error };
    },
  };
}

describe('EnrollmentService', () => {
  test('enrollDevice creates device and returns secret', async () => {
    const mock = createMockSupabase(null, null);
    const service = new EnrollmentService(mock);
    const result = await service.enrollDevice('device-123', 'Test Phone', 'Pixel', '14');

    expect(result.success).toBe(true);
    expect(result.enrollmentSecret).toBeDefined();
    expect(typeof result.enrollmentSecret).toBe('string');
  });

  test('enrollDevice rejects duplicate device', async () => {
    const mock = createMockSupabase({ device_id: 'device-123' }, null);
    const service = new EnrollmentService(mock);
    const result = await service.enrollDevice('device-123', 'Test Phone', 'Pixel', '14');

    expect(result.success).toBe(false);
    expect(result.error).toBe('Device already enrolled');
  });

  test('verifyEnrollment returns true for valid secret', async () => {
    const mock = createMockSupabase({ device_id: 'device-123' }, null);
    const service = new EnrollmentService(mock);
    const result = await service.verifyEnrollment('device-123', 'valid-secret');

    expect(result).toBe(true);
  });

  test('verifyEnrollment returns false for invalid secret', async () => {
    const mock = createMockSupabase(null, { message: 'Not found' });
    const service = new EnrollmentService(mock);
    const result = await service.verifyEnrollment('device-123', 'wrong-secret');

    expect(result).toBe(false);
  });

  test('getDevice returns null for unknown device', async () => {
    const mock = createMockSupabase(null, { message: 'Not found' });
    const service = new EnrollmentService(mock);
    const result = await service.getDevice('unknown');

    expect(result).toBeNull();
  });
});
