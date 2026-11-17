#!/usr/bin/env node
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const PROTO = process.env.PROTO;
const OUT_DIR = process.env.OUT_DIR;
if (!PROTO || !OUT_DIR) throw new Error('Set PROTO and OUT_DIR to absolute paths.');

const states = [
  { id: 'browse-default', screen: 'shop' },
  { id: 'browse-address-confirmed', screen: 'shop', addressConfirmed: true },
  { id: 'browse-category-brochette', screen: 'shop', category: 'Brochette' },
  { id: 'browse-category-chips', screen: 'shop', category: 'Chips' },
  { id: 'browse-category-bread', screen: 'shop', category: 'Bread' },
  { id: 'browse-category-coffee', screen: 'shop', category: 'Coffee' },
  { id: 'browse-category-juice', screen: 'shop', category: 'Juice' },
  { id: 'browse-no-results', screen: 'shop', category: 'No matching category' },
  { id: 'browse-sort-fastest', screen: 'shop', sort: 'fast' },
  { id: 'browse-sort-top-rated', screen: 'shop', sort: 'rating' },
  { id: 'browse-sort-free-delivery', screen: 'shop', sort: 'free' },
  { id: 'browse-sort-nearest', screen: 'shop', sort: 'near' },
  { id: 'restaurant-kigali-bakery-delivery', screen: 'erest', restaurant: 0 },
  { id: 'restaurant-nyamirambo-grill-delivery', screen: 'erest', restaurant: 1 },
  { id: 'restaurant-mama-rice-house-delivery', screen: 'erest', restaurant: 2 },
  { id: 'restaurant-kimironko-coffee-delivery', screen: 'erest', restaurant: 3 },
  { id: 'restaurant-kigali-bakery-pickup', screen: 'erest', restaurant: 0, pickup: true },
  { id: 'restaurant-kigali-bakery-cart-one', screen: 'erest', restaurant: 0, cart: { 0: 1 } },
  { id: 'checkout-delivery-below-minimum', screen: 'echeck', restaurant: 0, cart: { 0: 1 } },
  { id: 'checkout-delivery-filled', screen: 'echeck', restaurant: 0, cart: { 0: 2 } },
  { id: 'checkout-delivery-rider-call', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, request: 'Call when nearby' },
  { id: 'checkout-pickup-filled', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, pickup: true },
  { id: 'checkout-insufficient-balance', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, pay: 1000 },
  { id: 'payment-confirm-sheet', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, sheet: 'payment' },
  { id: 'tracking-delivery-accepted', screen: 'etrack', restaurant: 0, step: 0 },
  { id: 'tracking-delivery-preparing', screen: 'etrack', restaurant: 0, step: 1 },
  { id: 'tracking-delivery-on-the-way', screen: 'etrack', restaurant: 0, step: 2 },
  { id: 'tracking-delivery-complete', screen: 'etrack', restaurant: 0, step: 3 },
  { id: 'tracking-pickup-accepted', screen: 'etrack', restaurant: 0, pickup: true, step: 0 },
  { id: 'tracking-pickup-preparing', screen: 'etrack', restaurant: 0, pickup: true, step: 1 },
  { id: 'tracking-pickup-ready', screen: 'etrack', restaurant: 0, pickup: true, step: 2 },
  { id: 'tracking-pickup-complete', screen: 'etrack', restaurant: 0, pickup: true, step: 3 },
];

function safe(value) {
  return String(value).replace(/[^a-z0-9-]+/gi, '-').toLowerCase();
}

async function applyState(page, state) {
  await page.evaluate((s) => {
    const stackByScreen = {
      shop: ['shop'],
      erest: ['shop', 'erest'],
      echeck: ['shop', 'erest', 'echeck'],
      etrack: ['etrack'],
    };
    const layer = document.getElementById('layer');
    if (layer) layer.innerHTML = '';
    S.tab = 'home';
    S.stack = stackByScreen[s.screen];
    S.shopTab = 'eats';
    S.addrOk = !!s.addressConfirmed;
    S.ecat = s.category || 'All';
    S.esort = s.sort || 'fast';
    S.er = s.restaurant === undefined ? 0 : s.restaurant;
    S.ecart = Object.assign({}, s.cart || {});
    S.pickup = !!s.pickup;
    S.req = s.request || 'Leave at the door';
    S.trk = s.step || 0;
    S.pay = s.pay === undefined ? 120500 : s.pay;
    S.orders = s.screen === 'etrack' ? [{
      k: 'Eats order', s: 'Order accepted', t: 2400, r: S.er,
      items: [{ k: 0, q: 2 }], pickup: !!s.pickup, note: '',
      id: 'EA-7207', rate: 0, eta: Date.now() + 20 * 60000,
      addr: 'Kimironko, KG 11 Ave', snap: { sub: 2400, disc: 0, fee: 0, tip: 0 },
      pm: 'pay'
    }] : [];
    S.scart = {};
    S.oN = null;
    S.oE = null;
    S.eq = '';
    S.efav = {};
    S.efavOnly = false;
    S.eflt = { open: 0, top: 0, near: 0 };
    S.emq = '';
    S.etab = 'menu';
    S.etip = 0;
    S.enote = '';
    S.epromo = null;
    S.eai = 0;
    S.eaddrs = [['Home', 'Kimironko, KG 11 Ave'], ['Work', 'Kacyiru, KG 7 Ave']];
    S.esched = null;
    S.epm = 'pay';
    S.egrp = null;
    S.egsel = [];
    S.egadd = false;
    S.eik = 0;
    S.eiq = 1;
    S.eipf = [];
    S.eiad = [];
    S.cpn = [];
    S.cpIn = '';
    S.cpUsed = {};
    S.e2cat = 'All';
    S.e2sort = 'fast';
    S.e2q = '';
    S.e2flt = { open: 0, top: 0, near: 0 };
    S.e2mq = '';
    S.ervH = null;
    S.ervS = null;
    S.erec = null;
    S.ezAll = false;
    S.eapend = null;
    S.eadraft = null;
    S.eanote = '';
    S.eaform = null;
    S.ealab = '';
    S.lang = 'en';
    render('fade-none');
    if (s.sheet === 'payment') ePay();
  }, state);
  await page.waitForTimeout(180);
}

async function measure(page) {
  return page.evaluate(() => {
    const root = document.getElementById('sc');
    if (!root) throw new Error('Prototype screen root #sc was not found.');
    const out = ['scrollTop=' + root.scrollTop + ' scrollHeight=' + root.scrollHeight + ' clientHeight=' + root.clientHeight];
    const walk = (element, depth) => {
      if (depth > 8) return;
      for (const child of element.children) {
        const rect = child.getBoundingClientRect();
        if (rect.width < 1 || rect.height < 1) continue;
        const style = getComputedStyle(child);
        const className = typeof child.className === 'string' ? child.className.split(' ').join('.') : '';
        const text = child.childElementCount ? '' : (child.textContent || '').trim().slice(0, 30);
        out.push([
          depth,
          child.tagName.toLowerCase() + (className ? '.' + className : ''),
          rect.x.toFixed(1),
          rect.y.toFixed(1),
          rect.width.toFixed(1),
          rect.height.toFixed(1),
          style.fontSize,
          style.fontWeight,
          style.color,
          style.backgroundColor,
          style.backgroundImage.slice(0, 140),
          style.borderRadius,
          style.boxShadow.slice(0, 80),
          style.letterSpacing,
          style.lineHeight,
          text,
        ].join(' | '));
        walk(child, depth + 1);
      }
    };
    walk(root, 0);
    return out.join('\n');
  });
}

async function capture(page, state, theme, height, scrollTop) {
  await page.evaluate((top) => {
    const root = document.getElementById('sc');
    root.scrollTop = top;
  }, scrollTop);
  await page.waitForTimeout(80);
  const scrollLabel = scrollTop === 0 ? 'top' : 'scroll-' + scrollTop;
  const stem = [safe(state.id), theme, 'h' + height, scrollLabel].join('-');
  fs.writeFileSync(path.join(OUT_DIR, stem + '.txt'), await measure(page));
  await page.screenshot({ path: path.join(OUT_DIR, stem + '.png'), animations: 'disabled' });
}

(async () => {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  try {
    for (const theme of ['light', 'dark']) {
      for (const height of [2600, 844]) {
        const context = await browser.newContext({
          viewport: { width: 390, height },
          deviceScaleFactor: 3,
          colorScheme: theme,
        });
        const page = await context.newPage();
        await page.goto('file://' + PROTO, { waitUntil: 'load' });
        await page.waitForTimeout(1500);
        await page.evaluate(() => obSkip());
        await page.waitForTimeout(350);
        await page.evaluate((mode) => {
          setTheme(mode);
          document.documentElement.setAttribute('data-theme', mode);
        }, theme);
        await page.evaluate(() => document.fonts.ready);
        const metadata = await page.evaluate(() => ({
          title: document.title,
          screenKeys: Object.keys(screens),
          stateKeys: Object.keys(S),
          restaurants: REST.map((r) => ({
            name: r.n, category: r.cat, rating: r.r, reviews: r.rc,
            distanceKm: r.d, deliveryTime: r.t, deliveryFee: r.fee,
            minimumOrder: r.min, menu: r.items.map((item) => item[0]),
          })),
          categories: ECATS,
          sorts: ESORT,
          eatsHandlers: Object.keys(window).filter((key) =>
            /^(e|eg|epm|openCoupons|cp)/i.test(key)
          ).sort(),
        }));
        fs.writeFileSync(
          path.join(OUT_DIR, 'prototype-metadata-' + theme + '-h' + height + '.json'),
          JSON.stringify(metadata, null, 2)
        );

        for (const state of states) {
          await applyState(page, state);
          const maxScroll = await page.evaluate(() => {
            const root = document.getElementById('sc');
            return Math.max(0, root.scrollHeight - root.clientHeight);
          });
          let positions = [0];
          if (height === 844 && state.screen === 'shop' && state.id === 'browse-default') {
            positions = [0, Math.round(maxScroll / 2), maxScroll];
          } else if (height === 844 && ['erest', 'echeck', 'etrack'].includes(state.screen)) {
            positions = [0, maxScroll];
          }
          positions = [...new Set(positions)];
          for (const top of positions) await capture(page, state, theme, height, top);
        }
        await context.close();
      }
    }
    fs.writeFileSync(path.join(OUT_DIR, 'manifest.json'), JSON.stringify({
      source: path.basename(PROTO),
      deviceScaleFactor: 3,
      viewportWidths: [390],
      viewportHeights: [2600, 844],
      themes: ['light', 'dark'],
      states: states.map((state) => state.id),
    }, null, 2));
  } finally {
    await browser.close();
  }
})().catch((error) => {
  process.stderr.write(String(error.stack || error) + '\n');
  process.exitCode = 1;
});
