/**
 * 离线自检：mock GitHub Release API，验证 feed 协议与直链下载。
 * 运行：node smoke-test.mjs（不需要网络、令牌或 Cloudflare 账号）
 */
import assert from 'node:assert/strict';
import worker from './worker.js';

const SHA = 'b'.repeat(64);
const GH_URL = 'https://github.com/me/iji/releases/download/v1.0.0/iJi-1.0.0.apk';

function resetCache() {
  const store = new Map();
  globalThis.caches = {
    default: {
      async match(key) {
        const hit = store.get(key.url);
        return hit ? hit.clone() : null;
      },
      async put(key, value) {
        store.set(key.url, value);
      },
    },
  };
}

function mockLatest(handler) {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push(String(url));
    const u = new URL(url);
    if (u.pathname.endsWith('/releases/latest')) return handler(u, init);
    return new Response('not found', { status: 404 });
  };
  return calls;
}

function jsonResponse(obj) {
  return new Response(JSON.stringify(obj), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const env = { GH_OWNER: 'me', GH_REPO: 'iji' };

async function getFeed(customEnv = env) {
  const resp = await worker.fetch(new Request('https://iji-update.example.workers.dev/'), customEnv);
  return { resp, body: await resp.clone().json() };
}

function releaseWith(assets, tag = 'v1.0.0', body = '修复了若干问题') {
  return jsonResponse({ tag_name: tag, body, assets });
}

async function run() {
  // 1. 正常：tag / changelog / 附件 / digest / size
  resetCache();
  const calls = mockLatest(() => releaseWith([{
    id: 1,
    name: 'iJi-1.0.0.apk',
    size: 4096,
    state: 'uploaded',
    digest: `sha256:${SHA}`,
    browser_download_url: GH_URL,
  }]));
  let { resp, body } = await getFeed();
  assert.equal(resp.status, 200);
  assert.equal(body.code, 1);
  assert.equal(body.version, '1.0.0');
  assert.equal(body.changelog, '修复了若干问题');
  assert.equal(body.downloadUrl, GH_URL);
  assert.equal(body.fileName, 'iJi-1.0.0.apk');
  assert.equal(body.size, 4096);
  assert.equal(body.sha256, SHA);
  assert.ok(!body.downloadUrl.includes('token'), '下载地址不应带任何令牌');
  assert.ok(calls[0].includes('/repos/me/iji/releases/latest'));

  // 2. /apk 兼容入口 302 到直链
  let apk = await worker.fetch(new Request('https://iji-update.example.workers.dev/apk'), env);
  assert.equal(apk.status, 302);
  assert.equal(apk.headers.get('Location'), GH_URL);

  // 3. 多个附件时按 APK_ASSET_PATTERN 挑
  resetCache();
  mockLatest(() => releaseWith([
    { name: 'mapping.txt', size: 10, state: 'uploaded', browser_download_url: 'https://github.com/me/iji/releases/download/v1.0.0/mapping.txt' },
    { name: 'iJi-1.0.0.apk', size: 4096, state: 'uploaded', browser_download_url: GH_URL },
  ]));
  ({ body } = await getFeed());
  assert.equal(body.fileName, 'iJi-1.0.0.apk');

  // 4. 失败路径要如实报错，不冒充「已是最新」
  resetCache();
  mockLatest(() => releaseWith([{ name: 'iJi.apk', size: 1, state: 'uploaded', browser_download_url: GH_URL }], 'v1.0.0-Beta'));
  let failed = await worker.fetch(new Request('https://x.workers.dev/'), env);
  assert.equal(failed.status, 502);
  assert.ok((await failed.text()).includes('x.x.x'));

  resetCache();
  mockLatest(() => releaseWith([]));
  failed = await worker.fetch(new Request('https://x.workers.dev/'), env);
  assert.equal(failed.status, 502);
  assert.ok((await failed.text()).includes('附件'));

  resetCache();
  mockLatest(() => releaseWith([{ name: 'iJi.apk', size: 1, state: 'uploaded' }]));
  failed = await worker.fetch(new Request('https://x.workers.dev/'), env);
  assert.equal(failed.status, 502);
  assert.ok((await failed.text()).includes('browser_download_url'));

  resetCache();
  mockLatest(() => releaseWith([{
    name: 'iJi.apk',
    size: 1,
    state: 'uploaded',
    browser_download_url: 'http://insecure.example.com/iJi.apk',
  }]));
  failed = await worker.fetch(new Request('https://x.workers.dev/'), env);
  assert.equal(failed.status, 502);
  assert.ok((await failed.text()).includes('https'));

  // 5. 未配置 / 占位符未改
  const missing = await worker.fetch(new Request('https://x.workers.dev/'), {});
  assert.equal(missing.status, 502);
  assert.ok((await missing.text()).includes('GH_OWNER'));

  const placeholder = await worker.fetch(
    new Request('https://x.workers.dev/'),
    { GH_OWNER: 'me', GH_REPO: '改成公开仓库名' },
  );
  assert.equal(placeholder.status, 502);
  assert.ok((await placeholder.text()).includes('GH_REPO'));

  // 6. health
  resetCache();
  mockLatest(() => releaseWith([{ name: 'iJi.apk', size: 10, state: 'uploaded', browser_download_url: GH_URL }]));
  const health = await worker.fetch(new Request('https://x.workers.dev/health'), env);
  assert.equal(health.status, 200);
  const healthBody = await health.json();
  assert.equal(healthBody.ok, true);
  assert.equal(healthBody.repo, 'me/iji');
  assert.equal(healthBody.downloadHost, 'github.com');

  console.log('all smoke tests passed');
}

run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
