let ogs;
try { ogs = require('open-graph-scraper'); } catch { ogs = null; }

const ogCache = new Map();
const OG_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 часа

async function fetchOgData(url) {
  if (!ogs) return null;

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
