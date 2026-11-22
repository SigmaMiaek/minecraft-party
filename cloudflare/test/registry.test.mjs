import test from 'node:test';
import assert from 'node:assert/strict';
import { runChecks } from './checks.mjs';

test('directory authorization, expiry, collisions and abuse limits', () => {
  assert.equal(runChecks().length, 13);
});
