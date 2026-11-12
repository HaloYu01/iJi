/**
 * iJi 应用内更新源（Cloudflare Worker）—— GitHub 公开仓库 Release 版。
 *
 * 私有仓库只放源码；APK 上传到公开仓库（默认 HaloYu01/iJi-release）的 latest release，
 * Worker 匿名读 GitHub API 并翻译成客户端协议，全链路无令牌、不代理安装包。
 *
 * 客户端协议见 app/src/main/java/com/iji/app/update/UpdateChecker.kt：
 *   GET /       -> {"code":1,"version":"x.x.x","changelog":"…","downloadUrl":"https://…/iJi.apk",…}
 *   GET /apk    -> 302 到 downloadUrl（兼容入口）
 *   GET /health -> 排障摘要
 *
 * 环境变量只有 GH_OWNER / GH_REPO，可选 APK_ASSET_PATTERN。
 */

const META_TTL_SECONDS = 60;
const UPSTREAM_TIMEOUT_MS = 3000;
const CHANGELOG_MAX_CHARS = 400;
const VERSION_PATTERN = /^[vV]?(\d+\.\d+\.\d+)$/;

class ConfigError extends Error {}
class UpstreamError extends Error {}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return text('Method Not Allowed', 405);
    }
    try {
      if (path === '/') return await feed(request, env);
      if (path === '/apk') return await apk(request, env);
      if (path === '/health') return await health(request, env);
      return text('Not Found', 404);
    } catch (e) {
      return failure(e, 502);
    }
  },
};

function text(body, status = 200) {
  return new Response(body, {
    status,
    headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' },
  });
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
      'Access-Control-Allow-Origin': '*',
    },
  });
}

/** 只回显我们自己构造过的错误，避免把底层 URL 之类的细节暴露给客户端 */
function failure(e, status) {
  const known = e instanceof ConfigError || e instanceof UpstreamError;
  console.error('[iji-update]', known ? e.message : `未处理异常：${e && e.name}`);
  return text(known ? e.message : '更新服务内部错误（详见 Worker 日志）', status);
}

function config(env) {
  const owner = String(env.GH_OWNER || '').trim();
  const repo = String(env.GH_REPO || '').trim();
  if (!owner || !repo || owner.includes('改成') || repo.includes('改成')) {
    throw new ConfigError('请先在 wrangler.toml 填好 GH_OWNER / GH_REPO（必须是公开仓库）');
  }
  let assetPattern;
  try {
    assetPattern = new RegExp(String(env.APK_ASSET_PATTERN || '\\.apk$'), 'i');
  } catch (_) {
    throw new ConfigError('APK_ASSET_PATTERN 不是合法正则');
  }
  return { owner, repo, assetPattern };
}

function timeoutSignal() {
  return typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function'
    ? AbortSignal.timeout(UPSTREAM_TIMEOUT_MS)
    : undefined;
}

async function fetchJson(url) {
  const resp = await fetch(url, {
    headers: { 'User-Agent': 'iji-update-worker', Accept: 'application/json' },
    signal: timeoutSignal(),
  });
  if (!resp.ok) throw new UpstreamError(`${new URL(url).pathname} 返回 HTTP ${resp.status}`);
  return resp.json();
}

function digestOf(asset) {
  return typeof asset.digest === 'string' && asset.digest.startsWith('sha256:')
    ? asset.digest.slice(7).toLowerCase()
    : null;
}

/** 匿名读 GitHub 公开仓库的 latest release（draft / pre-release 不会被返回） */
async function githubMeta(cfg) {
  const base = `https://api.github.com/repos/${encodeURIComponent(cfg.owner)}/${encodeURIComponent(cfg.repo)}`;
  const rel = await fetchJson(`${base}/releases/latest`);
  const asset = (rel.assets || []).find(
    (a) => cfg.assetPattern.test(a.name || '') && (!a.state || a.state === 'uploaded'),
  );
  if (!asset) throw new UpstreamError(`latest release 里没有匹配 ${cfg.assetPattern} 的 .apk 附件`);
  if (!asset.browser_download_url) throw new UpstreamError('附件缺少 browser_download_url');
  return normalizeMeta({
    version: rel.tag_name,
    changelog: rel.body,
    fileName: asset.name,
    downloadUrl: asset.browser_download_url,
    size: asset.size,
    sha256: digestOf(asset),
  });
}

/**
 * 客户端只认严格 x.x.x，标签带 Beta / 日期后缀会被判失败。
 * downloadUrl 必须是 https 直链，且匿名可 GET（私有的签名链接过几小时就失效，不要填）。
 */
function normalizeMeta(raw) {
  if (!raw || typeof raw !== 'object') {
    throw new UpstreamError('更新源返回的不是 JSON 对象');
  }
  const tag = String(raw.version ?? raw.tag_name ?? '').trim();
  const m = tag.match(VERSION_PATTERN);
  if (!m) throw new UpstreamError(`版本号必须是 x.x.x（可带 v 前缀），当前：${tag || '(空)'}`);

  const downloadUrl = String(raw.downloadUrl || '').trim();
  if (!/^https:\/\//.test(downloadUrl)) {
    throw new UpstreamError('downloadUrl 必须是 https 直链（可匿名 GET）');
  }

  const meta = {
    version: m[1],
    changelog: String(raw.changelog ?? raw.body ?? '').trim().slice(0, CHANGELOG_MAX_CHARS),
    fileName: String(raw.fileName || downloadUrl.split('/').pop() || `iji-${m[1]}.apk`),
    downloadUrl,
  };
  const size = Number(raw.size);
  if (Number.isFinite(size) && size > 0) meta.size = size;
  const sha = String(raw.sha256 || '').trim().toLowerCase();
  if (sha.length === 64) meta.sha256 = sha;
  return meta;
}

/** metadata 缓存 60 秒：客户端 callTimeout 只有 4 秒；也避开 GitHub 60 次/小时/IP 的匿名限流 */
async function loadMeta(request, env) {
  const cfg = config(env);
  const cacheKey = new Request(new URL('/__meta', request.url).toString());
  try {
    const hit = await caches.default.match(cacheKey);
    if (hit) return { cfg, meta: await hit.json() };
  } catch (_) {
    // 本地 wrangler dev / 非 Cloudflare 运行时可能没有 Cache API
  }
  const meta = await githubMeta(cfg);
  try {
    await caches.default.put(
      cacheKey,
      new Response(JSON.stringify(meta), {
        headers: { 'Cache-Control': `public, max-age=${META_TTL_SECONDS}` },
      }),
    );
  } catch (_) {}
  return { cfg, meta };
}

async function feed(request, env) {
  const { meta } = await loadMeta(request, env);
  return json({ code: 1, ...meta });
}

/** 兼容旧入口：客户端拿到的是 downloadUrl 直链，正常不会走这里 */
async function apk(request, env) {
  const { meta } = await loadMeta(request, env);
  return Response.redirect(meta.downloadUrl, 302);
}

async function health(request, env) {
  try {
    const { cfg, meta } = await loadMeta(request, env);
    return json({
      ok: true,
      repo: `${cfg.owner}/${cfg.repo}`,
      version: meta.version,
      fileName: meta.fileName,
      size: meta.size ?? null,
      sha256: meta.sha256 ?? null,
      downloadHost: new URL(meta.downloadUrl).hostname,
    });
  } catch (e) {
    const known = e instanceof ConfigError || e instanceof UpstreamError;
    return json({ ok: false, reason: known ? e.message : '更新服务内部错误' }, 503);
  }
}
