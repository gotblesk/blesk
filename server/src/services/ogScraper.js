let ogs;
try { ogs = require('open-graph-scraper'); } catch { ogs = null; }

const { URL } = require('url');
const dns = require('dns');
const { promisify } = require('util');
const dnsResolve = promisify(dns.resolve);

const ogCache = new Map();
const OG_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 часа

function isPrivateIP(ip) {
  const parts = ip.split('.').map(Number);
  if (parts[0] === 10) return true;
  if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true;
  if (parts[0] === 192 && parts[1] === 168) return true;
  if (parts[0] === 127) return true;
  if (parts[0] === 169 && parts[1] === 254) return true;
  if (ip === '0.0.0.0') return true;
  return false;
}

async function validateUrl(urlStr) {
  try {
    const parsed = new URL(urlStr);
    if (!['http:', 'https:'].includes(parsed.protocol)) return false;
    const hostname = parsed.hostname;
    if (/^\d+\.\d+\.\d+\.\d+$/.test(hostname)) {
      return !isPrivateIP(hostname);
    }
    if (hostname === 'localhost' || hostname === '[::1]') return false;
    try {
      const addresses = await dnsResolve(hostname);
      return !addresses.some(isPrivateIP);
    } catch { return false; }
  } catch { return false; }
}

async function fetchOgData(url) {
  if (!ogs) return null;
  if (!(await validateUrl(url))) return null;

  // Проверить кеш
  const cached = ogCache.get(url);
  if (cached && Date.now() - cached.ts < OG_CACHE_TTL) return cached.data;

  try {
    const { result } = await ogs({ url, timeout: 5000 });
    const data = {
      title: result.ogTitle || result.twitterTitle || null,
      description: result.ogDescription || result.twitterDescription || null,
      image: result.ogImage?.[0]?.url || null,
      siteName: result.ogSiteName || null,
      url: result.ogUrl || url,
    };
    ogCache.set(url, { data, ts: Date.now() });

    // Очистка старых записей кеша
    if (ogCache.size > 1000) {
      const now = Date.now();
      for (const [k, v] of ogCache) {
        if (now - v.ts > OG_CACHE_TTL) ogCache.delete(k);
      }
    }

    return data;
  } catch {
    return null;
  }
}

module.exports = { fetchOgData };
