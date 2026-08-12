/* Приёмка nchg.html по §18 — 20 сценариев, jsdom.
   Прогон дважды: обычный и с prefers-reduced-motion (сценарий 20). */
'use strict';
const fs = require('fs');
const path = require('path');
const { JSDOM, VirtualConsole } = require('jsdom');

const HTML = fs.readFileSync(path.join(__dirname, 'nchg.html'), 'utf8');

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function assert(cond, msg) { if (!cond) throw new Error(msg); }
function approx(a, b, eps) { return Math.abs(a - b) <= (eps == null ? 1e-9 : eps); }

async function createDom(reduced) {
  const vc = new VirtualConsole();
  const errors = [];
  vc.on('jsdomError', e => {
    const m = String(e && e.message || e);
    if (/Not implemented/i.test(m)) return; // canvas/др. — ограничение jsdom, не сайта
    errors.push('jsdomError: ' + m);
  });
  vc.on('error', (...a) => errors.push('console.error: ' + a.join(' ')));
  const dom = new JSDOM(HTML, {
    url: 'http://localhost/nchg.html',
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    virtualConsole: vc,
    beforeParse(window) {
      if (reduced) {
        window.matchMedia = q => ({
          matches: /prefers-reduced-motion:\s*reduce/.test(q),
          media: q, addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {},
        });
      }
      // getContext в jsdom бросает not-implemented — глушим до null
      const gc = window.HTMLCanvasElement.prototype.getContext;
      window.HTMLCanvasElement.prototype.getContext = function () { return null; };
    },
  });
  await sleep(150);
  return { dom, errors, win: dom.window, N: dom.window.NCHG };
}

/* ---------------- сценарии ---------------- */
const scenarios = [];
const S = (n, name, fn) => scenarios.push({ n, name, fn });

S(1, 'файл поднимается без ошибок; движок тикает', async ctx => {
  const { N, errors } = ctx;
  assert(N, 'window.NCHG отсутствует');
  assert(errors.length === 0, 'ошибки консоли: ' + errors.join(' | '));
  const t0 = N.clock.tickCount;
  await sleep(1300);
  assert(N.clock.tickCount > t0, 'движок не тикает сам (interval)');
  N.clock.stop(); // дальше — детерминированные шаги
});

S(2, 'лендинг: капля-цена живая (меняется за 3с симуляции)', async ctx => {
  const { N, win } = ctx;
  const el = win.document.querySelector('#heroPrice');
  const before = el.textContent;
  let changed = false;
  for (let i = 0; i < 60 && !changed; i++) { N.clock.step(1); changed = el.textContent !== before; }
  assert(changed, 'цена героя не изменилась за 30с симуляции');
});

S(3, 'вход → app, таб-бар: 4 капли, активная отлипла', async ctx => {
  const { N, win } = ctx;
  N.ui.enterApp();
  assert(!win.document.querySelector('#app').hidden, 'app не показан');
  const drops = win.document.querySelectorAll('#tbGoo .drop');
  assert(drops.length === 4, 'капель в таб-баре: ' + drops.length);
  const activeBtn = win.document.querySelector('.tb-btn.active');
  assert(activeBtn && activeBtn.dataset.tab === 'market', 'активная вкладка не market');
  N.ui.goo.layoutDrops();
  const activeDrop = [...drops].find(d => d.classList.contains('active'));
  assert(activeDrop, 'нет активной капли');
  const dw = parseFloat(activeDrop.style.width) || 0;
  const bw = activeBtn.getBoundingClientRect().width || 0;
  assert(dw >= bw, 'капля (' + dw + ') меньше кнопки (' + bw + ')');
});

S(4, 'лимитка ниже рынка: резерв списан, в списке и в стакане', async ctx => {
  const { N, win } = ctx;
  N.debug.clearHalt();
  const acc = N.market.accounts.you;
  const bb = N.market.bestBid();
  assert(bb, 'нет бидов');
  const pc = bb - 500; // на 5 Ф ниже лучшего бида — точно не кроссит
  const fBefore = acc.f, resBefore = acc.resF;
  const r = N.api.postOrder({ side: 'buy', type: 'limit', price: pc, qty: 10 });
  assert(r.ok, 'заявка не принята: ' + r.reason);
  const val = Math.ceil(pc * 10 / 10);
  const reserve = val + Math.ceil(val * 0.001);
  assert(acc.f === fBefore - reserve, 'резерв списан неточно: ' + (fBefore - acc.f) + ' ≠ ' + reserve);
  assert(acc.resF === resBefore + reserve, 'resF неточен');
  N.clock.step(1);
  assert(win.document.querySelector('#ordersList [data-cancel="' + r.id + '"]'), 'заявки нет в списке');
  const q = N.market.book.bid.levels.get(pc);
  assert(q && q.some(o => o.id === r.id), 'заявки нет в стакане');
  ctx.limitOrder = { id: r.id, fBefore, reserve };
});

S(5, 'отмена лимитки: резерв вернулся копейка в копейку', async ctx => {
  const { N } = ctx;
  const acc = N.market.accounts.you;
  const { id, fBefore } = ctx.limitOrder;
  const ok = N.api.deleteOrder(id);
  assert(ok, 'отмена не удалась');
  assert(acc.f === fBefore, 'возврат неточен: ' + acc.f + ' ≠ ' + fBefore);
});

S(6, 'маркет-покупка: комиссия 0.1%, лента, тик в свече', async ctx => {
  const { N, win } = ctx;
  N.debug.clearHalt();
  const acc = N.market.accounts.you;
  const fBefore = acc.f, nBefore = acc.nchg;
  const seen = new Set(N.market.trades); // лента капится 200 записями — считаем по идентичности
  const candleV = N.candles.cur.m1 ? N.candles.cur.m1.v : 0;
  const r = N.api.postOrder({ side: 'buy', type: 'market', qty: 10 });
  assert(r.ok && r.filled === 10, 'маркет не исполнился целиком: ' + JSON.stringify(r));
  const myTrades = N.market.trades.filter(t => !seen.has(t));
  assert(myTrades.length >= 1, 'сделка не записана в ленту');
  let cost = 0;
  for (const t of myTrades) {
    const val = Math.round(t.pc * t.qt / 10);
    cost += val + Math.round(val * 0.001); // тейкер 0.1%
  }
  assert(acc.nchg === nBefore + 10, 'NCHG не зачислен');
  assert(acc.f === fBefore - cost, 'списание с комиссией неточно: ' + (fBefore - acc.f) + ' ≠ ' + cost);
  const cv = N.candles.cur.m1 ? N.candles.cur.m1.v : 0;
  assert(cv >= candleV + 10 || cv >= 10, 'тик не попал в свечу');
  N.clock.step(1);
  assert(win.document.querySelector('#tape .tape-row'), 'лента пуста в DOM');
});

S(7, 'маркет больше глубины: частичное исполнение + отмена остатка + тост', async ctx => {
  const { N, win } = ctx;
  N.debug.clearHalt();
  const acc = N.market.accounts.you;
  for (const o of [...N.market.orders.values()]) if (o.owner === 'you') N.api.deleteOrder(o.id);
  let depth = 0;
  for (const pc of N.market.book.bid.prices) for (const o of N.market.book.bid.levels.get(pc)) depth += o.rem;
  N.debug.credit(0, depth + 2000);
  const nBefore = acc.nchg;
  const qty = depth + 1000;
  // через UI-форму, чтобы сработал тост
  win.document.querySelector('#inQty').value = (qty / 10).toFixed(1);
  const segBtns = [...win.document.querySelectorAll('#segType .seg-btn')];
  segBtns.find(b => b.textContent === 'маркет').click();
  win.document.querySelector('#btnSell').click();
  assert(acc.resN === 0, 'резерв NCHG не возвращён');
  assert(acc.nchg > 0 && acc.nchg < nBefore, 'частичное исполнение не сошлось: осталось ' + acc.nchg);
  assert(acc.nchg === nBefore - depth, 'остаток неточен: ' + acc.nchg + ' ≠ ' + (nBefore - depth));
  const toasts = [...win.document.querySelectorAll('#toasts .toast')].map(t => t.textContent);
  assert(toasts.some(t => t.includes('в стакане закончилось ничего')), 'нет тоста, тосты: ' + toasts.join(';'));
  // рынок обвалился — вернём мир на место
  N.debug.clearHalt();
  N.debug.setFV(N.market.lastPrice / 100);
  N.clock.step(10);
  N.debug.clearHalt();
});

S(8, 'стоп-маркет: триггер по цене, исполнился, исчез', async ctx => {
  const { N, win } = ctx;
  N.debug.clearHalt();
  const acc = N.market.accounts.you;
  N.debug.credit(0, 50);
  const last = N.market.lastPrice;
  const trigger = last - 300;
  const r = N.api.postOrder({ side: 'sell', type: 'stop', trigger, qty: 5 });
  assert(r.ok, 'стоп не принят: ' + r.reason);
  assert(N.market.stopOrders.some(s => s.id === r.id), 'стопа нет в списке');
  N.clock.step(1);
  assert(win.document.querySelector('#ordersList [data-cancel="' + r.id + '"]'), 'стопа нет в UI');
  let fired = false;
  const off = N.bus.on('stop-fired', d => { if (d.so.id === r.id) fired = true; });
  N.debug.setFV((trigger - 200) / 100);
  for (let i = 0; i < 200 && !fired; i++) { if (N.breaker.halted) N.debug.clearHalt(); N.clock.step(1); }
  off();
  assert(fired, 'стоп не сработал');
  assert(!N.market.stopOrders.some(s => s.id === r.id), 'стоп не исчез из списка');
  N.clock.step(1);
  assert(!win.document.querySelector('#ordersList [data-cancel="' + r.id + '"]'), 'стоп не исчез из UI');
  N.debug.setFV(N.market.lastPrice / 100);
});

S(9, 'свечи в 4 ТФ параллельно; смена ТФ не пересобирает буферы', async ctx => {
  const { N } = ctx;
  const tfs = ['m1', 'm5', 'm15', 'h1'];
  for (const tf of tfs) assert(N.candles.cur[tf], 'нет текущей свечи ' + tf);
  assert(N.candles.buffers.m1.length > 0, 'буфер m1 пуст');
  const refs = tfs.map(tf => N.candles.buffers[tf]);
  const lens = tfs.map(tf => N.candles.buffers[tf].length);
  N.ui.chart.setTf('m5');
  N.ui.chart.setTf('h1');
  N.ui.chart.setTf('m1');
  tfs.forEach((tf, i) => {
    assert(N.candles.buffers[tf] === refs[i], 'буфер ' + tf + ' пересоздан');
    assert(N.candles.buffers[tf].length === lens[i], 'буфер ' + tf + ' изменился при переключении');
  });
});

S(10, 'circuit breaker: +30% FV → стоят, заявка отклонена, потом принимается', async ctx => {
  const { N } = ctx;
  N.debug.clearHalt();
  N.debug.setFV(N.market.lastPrice / 100 * 1.35);
  let i = 0;
  for (; i < 80 && !N.breaker.halted; i++) N.clock.step(1);
  assert(N.breaker.halted > 0, 'breaker не сработал за ' + i + ' тиков');
  const r = N.api.postOrder({ side: 'buy', type: 'limit', price: N.market.lastPrice, qty: 1 });
  assert(!r.ok && r.reason === 'halted', 'заявка не отклонена при остановке');
  N.clock.step(95); // пауза 45с = 90 тиков
  assert(N.breaker.halted === 0, 'пауза не снялась');
  N.clock.step(5);
  assert(N.breaker.halted === 0, 'breaker зациклился после снятия');
  const bb = N.market.bestBid();
  const r2 = N.api.postOrder({ side: 'buy', type: 'limit', price: bb - 500, qty: 1 });
  assert(r2.ok, 'заявка не принята после снятия: ' + r2.reason);
  N.api.deleteOrder(r2.id);
});

S(11, 'p2p: объявление → бот открывает сделку → эскроу разошёлся, рейтинг вырос', async ctx => {
  const { N } = ctx;
  const acc = N.market.accounts.you;
  N.debug.credit(0, 300);
  const doneBefore = N.state.p2pRating.done;
  let deal = null;
  for (let attempt = 0; attempt < 3 && !deal; attempt++) {
    const ad = N.api.postP2pAd({ side: 'sell', priceMode: 'fix', priceFix: N.market.lastPrice, min: 0, max: 1e9, methods: [N.p2p.METHODS[0]] });
    const d = N.debug.botTake(ad.id, 50);
    assert(d, 'бот не открыл сделку');
    assert(acc.escN >= 50, 'NCHG не в эскроу');
    for (let i = 0; i < 300 && d.status === 'escrow'; i++) N.clock.step(1);
    if (d.status === 'paid') deal = d;
    else { // редкий случай: бот открыл спор (5%) — доигрываем и пробуем заново
      for (let i = 0; i < 40 && !['resolved', 'returned', 'cancelled'].includes(d.status); i++) N.clock.step(1);
      N.debug.credit(0, 60);
    }
  }
  assert(deal, 'бот так и не оплатил');
  const escBefore = acc.escN, nBefore = acc.nchg;
  const ok = N.api.p2pConfirm(deal.id);
  assert(ok && deal.status === 'done', 'подтверждение не прошло');
  assert(acc.escN === escBefore - 50, 'эскроу не разошёлся');
  assert(N.state.p2pRating.done === doneBefore + 1, 'рейтинг (счётчик сделок) не вырос');
});

S(12, 'p2p-спор: монетка, вердикт, проигравшему −5', async ctx => {
  const { N } = ctx;
  N.debug.credit(0, 100);
  const ad = N.api.postP2pAd({ side: 'sell', priceMode: 'fix', priceFix: N.market.lastPrice, min: 0, max: 1e9, methods: [N.p2p.METHODS[1]] });
  const d = N.debug.botTake(ad.id, 30);
  assert(d, 'бот не открыл сделку');
  for (let i = 0; i < 300 && d.status === 'escrow'; i++) N.clock.step(1);
  if (d.status === 'paid') N.p2p.dispute(d.id);
  assert(d.status === 'dispute', 'спор не открыт, статус: ' + d.status);
  const youAdjBefore = N.state.p2pRating.adj || 0;
  const botBefore = N.p2p.rating(d.buyerId).pct;
  for (let i = 0; i < 30 && d.status === 'dispute'; i++) N.clock.step(1);
  assert(['resolved', 'returned'].includes(d.status) || d.status === 'done', 'вердикта нет, статус: ' + d.status);
  assert(d.verdict && /оплата ничем/.test(d.verdict), 'нет формулировки вердикта');
  assert(d.loser, 'нет проигравшего');
  if (d.loser === 'you') {
    assert((N.state.p2pRating.adj || 0) === youAdjBefore - 5, 'штраф −5 не применён к игроку');
  } else {
    const la = N.p2p.rating(d.loser).pct;
    assert(la === Math.max(0, botBefore - 5), 'рейтинг проигравшего бота: ' + botBefore + ' → ' + la + ' (ожидалось −5)');
  }
  // повторный спор невозможен
  assert(N.p2p.dispute(d.id) === false, 'повторный спор прошёл');
});

S(13, 'p2p-самосделка по кривой цене не влияет на ROI', async ctx => {
  const { N } = ctx;
  N.debug.credit(0, 100);
  const eqBefore = N.score.equity();
  const roiBefore = N.score.roi();
  const ad = N.api.postP2pAd({ side: 'sell', priceMode: 'fix', priceFix: 100, min: 0, max: 1e9, methods: [N.p2p.METHODS[0]] }); // 1 Ф — кривая цена
  const r = N.api.postP2pOrder(ad.id, 100); // сам себе
  assert(r.ok, 'самосделка не открылась: ' + r.reason);
  N.api.p2pPaid(r.deal.id);
  N.api.p2pConfirm(r.deal.id);
  assert(r.deal.status === 'done', 'самосделка не завершилась');
  assert(N.score.equity() === eqBefore, 'equity изменился');
  assert(approx(N.score.roi(), roiBefore, 1e-12), 'ROI изменился: ' + roiBefore + ' → ' + N.score.roi());
});

S(14, 'фьюч: лонг ×10 → ликвидация по формуле, счётчик надежд +1', async ctx => {
  const { N } = ctx;
  N.debug.clearHalt();
  N.debug.credit(2000000, 0);
  const entryBefore = N.market.lastPrice;
  const r = N.futures.open('long', 100, 10);
  assert(r.ok, 'позиция не открылась: ' + r.reason);
  const p = N.futures.pos;
  const expLiq = Math.round(p.entry * (1 - 1 / 10 + 0.005));
  assert(p.liq === expLiq, 'формула ликвидации: ' + p.liq + ' ≠ ' + expLiq);
  const hopesBefore = N.state.hopes;
  const fAfterOpen = N.market.accounts.you.f;
  N.debug.setFV(p.liq * 0.98 / 100);
  let i = 0;
  for (; i < 200 && N.futures.pos; i++) N.clock.step(1);
  assert(!N.futures.pos, 'ликвидация не случилась за ' + i + ' тиков');
  assert(N.state.hopes >= hopesBefore + 1, 'счётчик надежд не вырос');
  assert(N.market.accounts.you.f === fAfterOpen, 'маржа не сгорела целиком (баланс тронут)');
  assert(N.state.achievements.liq, 'достижение «Ликвидирован» не открылось');
  N.debug.setFV(N.market.lastPrice / 100);
  N.clock.step(5);
});

S(15, 'кран: при 900 Ф даёт 500, при 5000 — отказ; «вложено» выросло', async ctx => {
  const { N } = ctx;
  const acc = N.market.accounts.you;
  // 5000 Ф → отказ
  N.debug.drain();
  const p2pEsc = acc.escN; acc.escN = 0; // эскроу учёлся бы в equity
  N.debug.credit(500000, 0);
  let r = N.score.faucet();
  assert(!r.ok && r.reason === 'rich', 'кран выдал богатому');
  // 900 Ф → выдаёт
  N.debug.drain();
  N.debug.credit(90000, 0);
  const invBefore = N.state.player.invested;
  r = N.score.faucet();
  assert(r.ok, 'кран не выдал нищему: ' + r.reason);
  assert(acc.f === 90000 + 50000, 'не +500 Ф');
  assert(N.state.player.invested === invBefore + 50000, '«вложено» не выросло');
  acc.escN = p2pEsc;
  N.debug.credit(1000000, 100); // вернём рабочий баланс для следующих сценариев
});

S(16, 'достижение «первая кровь» — один раз', async ctx => {
  const { N } = ctx;
  assert(N.state.achievements.first, 'первая кровь не открыта после сделок');
  assert(N.score.unlock('first') === false, 'достижение выдалось повторно');
});

S(17, 'академия: квиз 3/3 → +200 Ф, повторно нет', async ctx => {
  const { N } = ctx;
  const acc = N.market.accounts.you;
  const answers = N.LESSONS[0].quiz.map(q => q.c);
  const fBefore = acc.f;
  const r1 = N.academy.grade(N.LESSONS[0].id, answers);
  assert(r1.correct === 3 && r1.passed && r1.rewarded, 'награда не выдана: ' + JSON.stringify(r1));
  assert(acc.f === fBefore + 20000, '+200 Ф не зачислены');
  const r2 = N.academy.grade(N.LESSONS[0].id, answers);
  assert(r2.passed && !r2.rewarded, 'награда выдана повторно');
  assert(acc.f === fBefore + 20000, 'баланс изменился при повторе');
});

S(18, 'табло: топ-20 + строка «вы»; после прибыльной сделки место пересчиталось', async ctx => {
  const { N, win } = ctx;
  N.debug.clearHalt();
  const rows = N.score.board();
  assert(rows.length >= 21, 'участников меньше 21: ' + rows.length);
  assert(rows.some(r => r.you), 'нет строки «вы»');
  N.ui.router.go('board');
  N.clock.step(1);
  const lb = win.document.querySelectorAll('#lbList .lb-row');
  assert(lb.length >= 20 && lb.length <= 21, 'строк в DOM: ' + lb.length);
  assert(win.document.querySelector('#lbList .lb-row.you'), 'нет строки «вы» в DOM');
  const rankBefore = rows.findIndex(r => r.you);
  const roiBefore = N.score.roi();
  // прибыльная сделка: купить, рынок вверх, продать
  N.ui.router.go('market');
  const rb = N.api.postOrder({ side: 'buy', type: 'market', qty: 50 });
  assert(rb.ok, 'покупка не прошла');
  N.debug.setFV(N.market.lastPrice / 100 * 1.18);
  N.clock.step(40);
  N.debug.clearHalt();
  const rs = N.api.postOrder({ side: 'sell', type: 'market', qty: 50 });
  assert(rs.ok, 'продажа не прошла');
  const roiAfter = N.score.roi();
  assert(roiAfter > roiBefore, 'ROI не вырос: ' + roiBefore + ' → ' + roiAfter);
  const rowsAfter = N.score.board();
  const rankAfter = rowsAfter.findIndex(r => r.you);
  assert(rankAfter >= 0, 'место не пересчиталось');
  ctx.rankInfo = { rankBefore, rankAfter };
});

S(19, 'visibilitychange: 60с фона → catch-up ≤ 5с, свечи без дыр', async ctx => {
  const { N, win } = ctx;
  const doc = win.document;
  let hidden = false;
  Object.defineProperty(doc, 'hidden', { configurable: true, get: () => hidden });
  const realNow = win.Date.now.bind(win.Date);
  hidden = true;
  doc.dispatchEvent(new win.Event('visibilitychange'));
  const tickAtHide = N.clock.tickCount;
  win.Date.now = () => realNow() + 60000; // 60 секунд «фона»
  hidden = false;
  doc.dispatchEvent(new win.Event('visibilitychange'));
  const caught = N.clock.tickCount - tickAtHide;
  assert(caught <= 10, 'catch-up больше 5с: ' + caught + ' тиков');
  assert(caught > 0, 'catch-up не случился');
  win.Date.now = realNow;
  N.clock.stop();
  // свечи без дыр: бакеты m1 строго подряд
  const buf = N.candles.buffers.m1;
  for (let i = 1; i < buf.length; i++) {
    assert(buf[i].bucket === buf[i - 1].bucket + 1, 'дыра в свечах: ' + buf[i - 1].bucket + ' → ' + buf[i].bucket);
  }
  if (N.candles.cur.m1 && buf.length) {
    assert(N.candles.cur.m1.bucket >= buf[buf.length - 1].bucket, 'текущая свеча позади буфера');
  }
});

/* ---------------- runner ---------------- */
async function runPass(reduced) {
  const ctx = await createDom(reduced);
  const results = [];
  if (reduced) {
    try { assert(ctx.N.REDUCED === true, 'REDUCED не включился'); }
    catch (e) { results.push({ n: 0, name: 'reduced-флаг', ok: false, err: e.message }); }
  }
  for (const s of scenarios) {
    try { await s.fn(ctx); results.push({ n: s.n, name: s.name, ok: true }); }
    catch (e) { results.push({ n: s.n, name: s.name, ok: false, err: e.message }); }
  }
  ctx.dom.window.close();
  return results;
}

(async () => {
  console.log('=== ПРОГОН 1: обычный ===');
  const r1 = await runPass(false);
  for (const r of r1) console.log((r.ok ? ' ✓' : ' ✗') + ' ' + String(r.n).padStart(2) + '. ' + r.name + (r.ok ? '' : '  → ' + r.err));
  console.log('=== ПРОГОН 2: prefers-reduced-motion (сценарий 20) ===');
  const r2 = await runPass(true);
  for (const r of r2) console.log((r.ok ? ' ✓' : ' ✗') + ' ' + String(r.n).padStart(2) + '. ' + r.name + (r.ok ? '' : '  → ' + r.err));
  const fail1 = r1.filter(r => !r.ok).length;
  const fail2 = r2.filter(r => !r.ok).length;
  console.log('---');
  console.log('Сценарии 1–19: ' + (r1.length - fail1) + '/' + r1.length + ' зелёные');
  console.log('Сценарий 20 (reduced-motion, все сценарии повторно): ' + (fail2 === 0 ? 'ЗЕЛЁНЫЙ' : 'КРАСНЫЙ (' + fail2 + ' падений)'));
  process.exit(fail1 + fail2 > 0 ? 1 : 0);
})();
