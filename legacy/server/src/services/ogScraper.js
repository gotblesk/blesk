let ogs;
try { ogs = require('open-graph-scraper'); } catch { ogs = null; }

const { URL } = require('url');
const dns = require('dns');
const { promisify } = require('util');
const dnsResolve = promisify(dns.resolve);

const ogCache = new Map();
const OG_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 часа

function isPrivateIP(ip) {
  // IPv6
  if (ip.includes(':')) {
    const normalized = ip.toLowerCase().replace(/\s/g, '');
    if (normalized === '::1') return true;
    if (normalized === '::') return true;
    // fc00::/7 — unique local (fc00:: и fd00::)
    if (normalized.startsWith('fc') || normalized.startsWith('fd')) return true;
    // fe80::/10 — link-local
    if (normalized.startsWith('fe80')) return true;
    // ::ffff:x.x.x.x — IPv4-mapped IPv6
    const v4mapped = normalized.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (v4mapped) return isPrivateIP(v4mapped[1]);
    return false;
  }
  // IPv4
  const parts = ip.split('.').map(Number);
  if (parts.length !== 4 || parts.some(p => isNaN(p))) return true;
  if (parts[0] === 10) return true;                              // 10.0.0.0/8
  if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true; // 172.16.0.0/12
  if (parts[0] === 192 && parts[1] === 168) return true;        // 192.168.0.0/16
  if (parts[0] === 127) return true;                              // 127.0.0.0/8
  if (parts[0] === 169 && parts[1] === 254) return true;        // 169.254.0.0/16
  if (parts[0] === 0) return true;                                // 0.0.0.0/8
  if (parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127) return true; // 100.64.0.0/10 (CGN)
  if (parts[0] === 198 && (parts[1] === 18 || parts[1] === 19)) return true; // 198.18.0.0/15 (benchmark)
  if (parts[0] >= 224) return true;                               // 224.0.0.0+ (multicast/reserved)
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
    // Отклонить IPv6-литералы в квадратных скобках
    if (hostname.startsWith('[')) return false;
    try {
      const addresses = await dnsResolve(hostname);
      if (addresses.some(isPrivateIP)) return false;
      // Проверить и AAAA записи (IPv6)
      try {
        const dnsResolve6 = promisify(dns.resolve6);
        const v6addresses = await dnsResolve6(hostname);
        if (v6addresses.some(isPrivateIP)) return false;
      } catch { /* Нет AAAA записей — ок */ }
      return true;
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

// Периодическая очистка просроченных записей кеша — раз в час
const ogCacheCleanupInterval = setInterval(() => {
  const now = Date.now();
  for (const [url, entry] of ogCache) {
    if (now - entry.ts > OG_CACHE_TTL) ogCache.delete(url);
  }
}, 60 * 60 * 1000);

module.exports = { fetchOgData, ogCacheCleanupInterval };
