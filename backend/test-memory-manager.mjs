import assert from 'node:assert/strict';
import { MemoryManager } from './src/core/memoryManager.js';

const calls = [];
const manager = new MemoryManager();
manager.available = true;
manager.supabase = {
  from(table) {
    return {
      upsert(row) {
        calls.push([table, row]);
        return { select: () => ({ single: async () => ({ data: { id: '00000000-0000-0000-0000-000000000001' }, error: null }) }) };
      },
      insert(row) {
        calls.push([table, row]);
        return { select: () => ({ single: async () => ({ data: row, error: null }) }) };
      },
    };
  },
};

await manager.store('android-Pixel', 'remember this');
assert.deepEqual(calls, [
  ['users', { device_id: 'android-Pixel' }],
  ['memories', {
    user_id: '00000000-0000-0000-0000-000000000001',
    content: 'remember this',
    memory_type: 'conversation',
    importance: 0.5,
    embedding: manager._dummyEmbedding('remember this'),
  }],
]);
