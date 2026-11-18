#!/usr/bin/env node
'use strict';

import fs from 'node:fs';
import path from 'node:path';

if (!process.env.PLAYWRIGHT_ENTRY) throw new Error('Set PLAYWRIGHT_ENTRY to Playwright index.mjs.');
const { chromium } = await import(process.env.PLAYWRIGHT_ENTRY);

const PROTO = process.env.PROTO;
const OUT_DIR = process.env.OUT_DIR;
if (!PROTO || !OUT_DIR) throw new Error('Set PROTO and OUT_DIR to absolute paths.');

const order = (pickup = false, status = 'On the way', id = 'EA-7207') => ({
  k: 'Eats order', s: status, t: 2400, r: 0, items: [{ k: 0, q: 2 }],
  pickup, note: 'Please call when you arrive.', id, rate: 0,
  eta: Date.now() + 20 * 60000, addr: 'Kimironko, KG 11 Ave',
  snap: { sub: 2400, disc: 0, fee: 0, tip: 0 }, pm: 'pay',
});

const states = [
  { id: 'browse-default', screen: 'shop' },
  { id: 'browse-address-confirmation', screen: 'shop', addrOk: false },
  { id: 'browse-pickup', screen: 'shop', pickup: true },
  { id: 'browse-address-work', screen: 'shop', addressIndex: 1 },
  { id: 'browse-search-bakery', screen: 'shop', query: 'bakery' },
  { id: 'browse-search-coffee', screen: 'shop', query: 'coffee' },
  { id: 'browse-search-no-results', screen: 'shop', query: 'zzzz' },
  { id: 'browse-filter-open-now', screen: 'shop', filters: { open: 1 } },
  { id: 'browse-filter-saved', screen: 'shop', favoritesOnly: true, favorite: { 0: true, 1: true } },
  { id: 'browse-category-brochette', screen: 'shop', category: 'Brochette' },
  { id: 'browse-category-chicken', screen: 'shop', category: 'Chicken' },
  { id: 'browse-category-pizza', screen: 'shop', category: 'Pizza' },
  { id: 'browse-category-burgers', screen: 'shop', category: 'Burgers' },
  { id: 'browse-category-local', screen: 'shop', category: 'Local' },
  { id: 'browse-category-chips', screen: 'shop', category: 'Chips' },
  { id: 'browse-category-bakery', screen: 'shop', category: 'Bakery' },
  { id: 'browse-category-coffee', screen: 'shop', category: 'Coffee' },
  { id: 'browse-category-juice', screen: 'shop', category: 'Juice' },
  { id: 'browse-category-healthy', screen: 'shop', category: 'Healthy' },
  { id: 'browse-category-dessert', screen: 'shop', category: 'Dessert' },
  { id: 'browse-empty-filter-combination', screen: 'shop', category: 'Brochette', filters: { open: 1 } },
  { id: 'browse-sort-fastest', screen: 'shop', sort: 'fast' },
  { id: 'browse-sort-top-rated', screen: 'shop', sort: 'rating' },
  { id: 'browse-sort-free-delivery', screen: 'shop', sort: 'free' },
  { id: 'browse-sort-nearest', screen: 'shop', sort: 'near' },
  { id: 'browse-promo-delivery-saved', screen: 'shop', promo: 'FREEDEL' },
  { id: 'browse-promo-pickup-saved', screen: 'shop', pickup: true, promo: 'EATS20' },
  { id: 'browse-coupons-sheet', screen: 'shop', actions: [{ fn: 'e2Cp', args: [] }] },
  { id: 'browse-coupons-freedel-saved', screen: 'shop', promo: 'FREEDEL', actions: [{ fn: 'e2Cp', args: [] }] },
  { id: 'browse-coupons-eats20-saved', screen: 'shop', pickup: true, promo: 'EATS20', actions: [{ fn: 'e2Cp', args: [] }] },
  { id: 'browse-address-sheet', screen: 'shop', actions: [{ fn: 'eAddr', args: [] }] },
  { id: 'browse-address-work-selected', screen: 'shop', addressIndex: 1, actions: [{ fn: 'eAddr', args: [] }] },
  { id: 'browse-address-new-form', screen: 'shop', actions: [{ fn: 'eAddr', args: [] }, { fn: 'eaForm', args: [1] }] },
  { id: 'browse-club-sheet', screen: 'shop', actions: [{ fn: 'clubSheet', args: [] }] },
  { id: 'browse-order-again', screen: 'shop', orderAgain: true },
  { id: 'browse-recently-viewed', screen: 'shop', recentlyViewed: [1, 2] },
  { id: 'browse-cart-cta', screen: 'shop', restaurant: 0, cart: { 0: 2 } },
  { id: 'restaurant-kigali-bakery-delivery', screen: 'erest', restaurant: 0 },
  { id: 'restaurant-kigali-bakery-pickup', screen: 'erest', restaurant: 0, pickup: true },
  { id: 'restaurant-info-tab', screen: 'erest', restaurant: 0, restaurantTab: 'info' },
  { id: 'restaurant-reviews-tab', screen: 'erest', restaurant: 0, restaurantTab: 'reviews' },
  { id: 'restaurant-menu-search', screen: 'erest', restaurant: 0, menuQuery: 'croissant' },
  { id: 'restaurant-item-sheet', screen: 'erest', restaurant: 0, actions: [{ fn: 'eItem', args: [0] }] },
  { id: 'restaurant-item-customized-sheet', screen: 'erest', restaurant: 0, actions: [{ fn: 'eItem', args: [0] }, { fn: 'eIp', args: [1] }, { fn: 'eIq', args: [1] }] },
  { id: 'restaurant-group-invite-sheet', screen: 'erest', restaurant: 0, actions: [{ fn: 'egStart', args: [] }] },
  { id: 'restaurant-group-manage-sheet', screen: 'erest', restaurant: 0, cart: { 0: 2, 1: 1 }, group: { r: 0, code: '4821', mode: 'all', members: [{ n: 'Amina K.', items: { 1: 1 }, joined: true }, { n: 'Eric M.', items: {}, joined: false }] }, actions: [{ fn: 'egManage', args: [] }] },
  { id: 'restaurant-menu-cart-filled', screen: 'erest', restaurant: 0, cart: { 0: 2 } },
  { id: 'checkout-empty', screen: 'echeck', restaurant: 0 },
  { id: 'checkout-delivery-below-minimum', screen: 'echeck', restaurant: 0, cart: { 0: 1 } },
  { id: 'checkout-delivery-filled', screen: 'echeck', restaurant: 0, cart: { 0: 2 } },
  { id: 'checkout-delivery-tip-1000', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, tip: 1000 },
  { id: 'checkout-delivery-note-filled', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, note: 'Please call when you arrive.' },
  { id: 'checkout-delivery-eats20', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, promo: 'EATS20' },
  { id: 'checkout-delivery-freedel', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, promo: 'FREEDEL' },
  { id: 'checkout-pickup-filled', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, pickup: true },
  { id: 'checkout-pickup-eats20', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, pickup: true, promo: 'EATS20' },
  { id: 'checkout-scheduled', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, scheduled: '18:30' },
  { id: 'checkout-payment-bank', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, paymentMethod: 'bank' },
  { id: 'checkout-payment-cash', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, paymentMethod: 'cash' },
  { id: 'checkout-insufficient-balance', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, pay: 1000 },
  { id: 'checkout-payment-method-sheet', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, actions: [{ fn: 'epmOpen', args: [] }] },
  { id: 'checkout-address-sheet', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, actions: [{ fn: 'eAddr', args: [] }] },
  { id: 'checkout-payment-confirm-sheet', screen: 'echeck', restaurant: 0, cart: { 0: 2 }, actions: [{ fn: 'ePay', args: [] }] },
  { id: 'checkout-group-split', screen: 'echeck', restaurant: 0, cart: { 0: 2, 1: 1 }, group: { r: 0, code: '4821', mode: 'split', members: [{ n: 'Amina K.', items: { 1: 1 }, joined: true }, { n: 'Eric M.', items: { 0: 1 }, joined: true }] } },
  { id: 'tracking-delivery-accepted', screen: 'etrack', restaurant: 0, step: 0 },
  { id: 'tracking-delivery-preparing', screen: 'etrack', restaurant: 0, step: 1 },
  { id: 'tracking-delivery-on-the-way', screen: 'etrack', restaurant: 0, step: 2 },
  { id: 'tracking-delivery-complete', screen: 'etrack', restaurant: 0, step: 3 },
  { id: 'tracking-pickup-accepted', screen: 'etrack', restaurant: 0, pickup: true, step: 0 },
  { id: 'tracking-pickup-preparing', screen: 'etrack', restaurant: 0, pickup: true, step: 1 },
  { id: 'tracking-pickup-ready', screen: 'etrack', restaurant: 0, pickup: true, step: 2 },
  { id: 'tracking-pickup-complete', screen: 'etrack', restaurant: 0, pickup: true, step: 3 },
  { id: 'tracking-rider-sheet', screen: 'etrack', restaurant: 0, actions: [{ fn: 'eRider', args: [] }] },
  { id: 'tracking-cancel-sheet', screen: 'etrack', restaurant: 0, actions: [{ fn: 'eCancel', args: [] }] },
  { id: 'tracking-rating-sheet', screen: 'etrack', restaurant: 0, actions: [{ fn: 'eRate', args: [] }] },
  { id: 'tracking-cancelled', screen: 'etrack', restaurant: 0, cancelled: true, step: 0 },
  { id: 'orders-empty', screen: 'eorders' },
  { id: 'orders-filled', screen: 'eorders', orders: [order(false, 'Delivered'), order(true, 'Cancelled', 'EA-7214')] },
  { id: 'orders-receipt-sheet', screen: 'eorders', orders: [order(false, 'Delivered')], actions: [{ fn: 'eRcpt', args: [0] }] },
  { id: 'orders-help-sheet', screen: 'eorders', orders: [order(false, 'Delivered')], actions: [{ fn: 'eHelp', args: [0] }] },
];

const stackByScreen = {
  shop: ['shop'],
  erest: ['shop', 'erest'],
  echeck: ['shop', 'erest', 'echeck'],
  etrack: ['etrack'],
  eorders: ['shop', 'eorders'],
};

function safe(value) {
  return String(value).replace(/[^a-z0-9-]+/gi, '-').toLowerCase();
}

async function applyState(page, state) {
  const payload = { ...state, defaultOrder: order(!!state.pickup, 'On the way') };
  await page.evaluate((s) => {
    const stackByScreen = {
      shop: ['shop'],
      erest: ['shop', 'erest'],
      echeck: ['shop', 'erest', 'echeck'],
      etrack: ['etrack'],
      eorders: ['shop', 'eorders'],
    };
    const layer = document.getElementById('layer');
    if (layer) layer.innerHTML = '';
    S.tab = 'home';
    S.stack = stackByScreen[s.screen] || ['shop'];
    S.shopTab = 'eats';
    S.addrOk = s.addrOk === undefined ? true : !!s.addrOk;
    S.ecat = s.category || 'All';
    S.e2cat = s.category || 'All';
    S.esort = s.sort || 'fast';
    S.e2sort = s.sort || 'fast';
    S.eq = s.query || '';
    S.e2q = s.query || '';
    S.efav = Object.assign({}, s.favorite || {});
    S.efavOnly = !!s.favoritesOnly;
    S.eflt = Object.assign({ open: 0, top: 0, near: 0 }, s.filters || {});
    S.e2flt = Object.assign({ open: 0, top: 0, near: 0, fav: s.favoritesOnly ? 1 : 0 }, s.filters || {});
    S.er = s.restaurant === undefined ? 0 : s.restaurant;
    S.ecart = Object.assign({}, s.cart || {});
    S.pickup = !!s.pickup;
    S.req = s.request || 'Leave at the door';
    S.trk = s.step || 0;
    S.pay = s.pay === undefined ? 120500 : s.pay;
    S.bank = 842300;
    S.etab = s.restaurantTab || 'menu';
    S.emq = s.menuQuery || '';
    S.e2mq = s.menuQuery || '';
    S.etip = s.tip || 0;
    S.enote = s.note || '';
    S.epromo = s.promo || null;
    if (S.pickup && S.epromo === 'FREEDEL') S.epromo = null;
    S.eai = s.addressIndex === undefined ? 0 : s.addressIndex;
    S.eaddrs = [['Home', 'Kimironko, KG 11 Ave', 'Kimironko, Kigali'], ['Work', 'Kacyiru, KG 7 Ave', 'Kacyiru, Kigali']];
    S.eapend = null;
    S.eaform = 0;
    S.eadraft = '';
    S.eanote = '';
    S.ealab = 'Other';
    S.esched = s.scheduled || null;
    S.epm = s.paymentMethod || 'pay';
    S.egrp = s.group || null;
    S.club = !!s.club;
    S.erec = s.recentlyViewed || [];
    S.egsel = [];
    S.egadd = false;
    S.eik = 0;
    S.eiq = 1;
    S.eipf = [];
    S.eiad = [];
    S.ecancel = !!s.cancelled;
    S.ervH = {};
    S.ervS = null;
    S.ezAll = false;
    S.cpn = [];
    S.cpIn = '';
    S.cpUsed = {};
    S.lang = 'en';
    if (s.orders) S.orders = s.orders;
    else if (s.orderAgain || s.screen === 'etrack') S.orders = [s.defaultOrder];
    else S.orders = [];
    S.scart = {};
    S.oN = null;
    S.oE = null;
    render('fade-none');
    for (const action of (s.actions || [])) {
      const fn = window[action.fn];
      if (typeof fn !== 'function') throw new Error('Prototype action not found: ' + action.fn);
      fn(...(action.args || []));
    }
  }, payload);
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
          rect.x.toFixed(1), rect.y.toFixed(1), rect.width.toFixed(1), rect.height.toFixed(1),
          style.fontSize, style.fontWeight, style.color, style.backgroundColor,
          style.backgroundImage.slice(0, 140), style.borderRadius, style.boxShadow.slice(0, 80),
          style.letterSpacing, style.lineHeight, text,
        ].join(' | '));
        walk(child, depth + 1);
      }
    };
    walk(root, 0);
    const layer = document.getElementById('layer');
    if (layer) walk(layer, 0);
    return out.join('\n');
  });
}

async function inventory(page) {
  return page.evaluate(() => {
    const root = document.getElementById('sc');
    const layer = document.getElementById('layer');
    const controls = [...document.querySelectorAll('#phone button, #phone input, #phone select, #phone [onclick], #phone [role="button"], #phone [role="switch"], #phone [role="radio"]')];
    return {
      screenKey: S.stack[S.stack.length - 1],
      screenText: ((root && root.innerText) || '').slice(0, 7000),
      sheetText: ((layer && layer.innerText) || '').slice(0, 3000),
      scroll: root ? { top: root.scrollTop, height: root.scrollHeight, client: root.clientHeight } : null,
      controls: controls.map((element) => ({
        tag: element.tagName.toLowerCase(),
        text: (element.innerText || element.value || '').trim().replace(/\s+/g, ' ').slice(0, 100),
        id: element.id,
        className: typeof element.className === 'string' ? element.className : '',
        role: element.getAttribute('role'),
        ariaLabel: element.getAttribute('aria-label'),
        onClick: element.getAttribute('onclick'),
        disabled: !!element.disabled,
      })),
    };
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
  fs.writeFileSync(path.join(OUT_DIR, stem + '.json'), JSON.stringify(await inventory(page), null, 2));
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
            name: r.n, categories: r.cats, rating: r.r, reviews: r.rc,
            distanceKm: r.d, deliveryMinutes: r.tm, deliveryFee: r.fee,
            minimumOrder: r.min, closed: r.closed, menu: r.items.map((item) => item[0]),
          })),
          categories: ['All', 'Brochette', 'Chicken', 'Pizza', 'Burgers', 'Local', 'Chips', 'Bakery', 'Coffee', 'Juice', 'Healthy', 'Dessert'],
          sorts: ['fast', 'rating', 'free', 'near'],
          eatsHandlers: Object.keys(window).filter((key) => /^(e|eg|epm|openCoupons|cp)/i.test(key)).sort(),
        }));
        fs.writeFileSync(path.join(OUT_DIR, 'prototype-metadata-' + theme + '-h' + height + '.json'), JSON.stringify(metadata, null, 2));

        for (const state of states) {
          await applyState(page, state);
          const maxScroll = await page.evaluate(() => {
            const root = document.getElementById('sc');
            return Math.max(0, root.scrollHeight - root.clientHeight);
          });
          // At phone height, capture the top, middle and bottom of every scrollable
          // state. The tall viewport remains the complete-content reference; these
          // three viewport captures make clipped/overlapping sticky elements visible.
          let positions = [0];
          if (height === 844 && maxScroll > 0) {
            positions = [0, Math.round(maxScroll / 2), maxScroll];
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
