const { test } = require('node:test');
const assert = require('node:assert/strict');
const { verifyCi, verifyPrivate } = require('./ghcr-policy.cjs');
const context = { repo: { owner: 'danielhw99', repo: 'example' }, sha: 'a'.repeat(40), payload: { repository: { id: 7 } } };
const run = { head_sha: context.sha, head_branch: 'dev', event: 'push', conclusion: 'success', head_repository: { id: 7 } };
const client = runs => ({ paginate: async () => runs, rest: { actions: { listWorkflowRuns: () => {} } } });
test('accepts CI success for exact source revision', async () => { await verifyCi(client([run]), context, 'ci.yml'); });
for (const [field, value] of [['head_sha', 'b'.repeat(40)], ['event', 'pull_request'], ['conclusion', 'failure'], ['head_repository', { id: 8 }]]) {
  test(`rejects mismatched CI ${field}`, async () => { await assert.rejects(verifyCi(client([{ ...run, [field]: value }]), context, 'ci.yml')); });
}
test('accepts existing private package', async () => { await verifyPrivate({ request: async () => ({ data: { visibility: 'private' } }) }, context, ['image'], false); });
test('rejects public package', async () => { await assert.rejects(verifyPrivate({ request: async () => ({ data: { visibility: 'public' } }) }, context, ['image'], false)); });
test('only preflight permits missing new package', async () => {
  const missing = { request: async () => { throw Object.assign(new Error('missing'), { status: 404 }); } };
  await verifyPrivate(missing, context, ['image'], true);
  await assert.rejects(verifyPrivate(missing, context, ['image'], false));
});
test('permission failures are never treated as new package', async () => { await assert.rejects(verifyPrivate({ request: async () => { throw Object.assign(new Error('denied'), { status: 403 }); } }, context, ['image'], true)); });
