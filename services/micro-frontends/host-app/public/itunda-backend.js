/* Real Itunda authentication and account data for the v66 consumer web experience. */
(function () {
  'use strict';
  const API_BASE = 'https://api.itunda.im';
  const ACCESS_KEY = 'itunda_web_access_token';
  const REFRESH_KEY = 'itunda_web_refresh_token';
  let mainAccountId = null;
  let liveTransactions = [];
  let liveUser = null;
  let busy = false;

  function normalizePhone(value) {
    const v = String(value || '').replace(/[\\s()-]/g, '');
    if (v.startsWith('+')) return v;
    if (v.startsWith('250') && v.length === 12) return '+' + v;
    if (v.startsWith('0') && v.length === 10) return '+250' + v.slice(1);
    if (/^\\d{9}$/.test(v)) return '+250' + v;
    return v;
  }
  async function api(path, options, retry) {
    options = options || {};
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    const access = localStorage.getItem(ACCESS_KEY);
    if (access) headers.Authorization = 'Bearer ' + access;
    let response;
    try { response = await fetch(API_BASE + path, Object.assign({}, options, { headers })); }
    catch (_) { throw new Error('Could not reach itunda. Check your connection and try again.'); }
    if (response.status === 401 && retry !== false && access && path !== '/api/v1/auth/refresh') {
      const refreshToken = localStorage.getItem(REFRESH_KEY);
      if (refreshToken) {
        try {
          const refreshed = await fetch(API_BASE + '/api/v1/auth/refresh', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: refreshToken })
          });
          if (refreshed.ok) {
            const session = await refreshed.json();
            localStorage.setItem(ACCESS_KEY, session.accessToken);
            localStorage.setItem(REFRESH_KEY, session.refreshToken);
            return api(path, options, false);
          }
        } catch (_) {}
      }
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
      throw new Error('Your session expired. Please sign in again.');
    }
    const body = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(body.message || body.error || ('Request failed (' + response.status + ').'));
    return body;
  }
  async function loadAccount() {
    const results = await Promise.all([
      api('/api/v1/account'),
      api('/api/v1/account/transactions').catch(function () { return { transactions: [] }; }),
      api('/api/v1/auth/profile').catch(function () { return null; })
    ]);
    const accountList = Array.isArray(results[0].accounts) ? results[0].accounts : [];
    liveTransactions = Array.isArray(results[1].transactions) ? results[1].transactions : [];
    if (results[2] && results[2].user) liveUser = results[2].user;
    const main = accountList.find(function (a) { return String(a.type || '').toUpperCase() === 'MAIN'; }) || accountList[0];
    if (!main) throw new Error('No account balance is available for this user yet.');
    mainAccountId = main.id;
    S.bank = Number(main.balance || 0);
    S.name = [liveUser && liveUser.firstName, liveUser && liveUser.lastName].filter(Boolean).join(' ') || S.name || '';
  }
  function fail(error) {
    if (typeof window.toast === 'function') window.toast(error.message || 'Something went wrong.');
    else alert(error.message || 'Something went wrong.');
  }

  screens.backendLogin = function () {
    const pin = S.ob.pin || '';
    const dots = Array.from({ length: 6 }, function (_, i) {
      return '<i style="width:12px;height:12px;border-radius:50%;display:inline-block;margin:0 8px;background:' + (i < pin.length ? 'var(--tx)' : 'var(--ln)') + '"></i>';
    }).join('');
    const keys = ['1','2','3','4','5','6','7','8','9','','0','del'].map(function (key) {
      if (!key) return '<span></span>';
      return '<button type="button" onclick="backendPinKey(\'' + key + '\')" style="height:56px;border:0;background:none;font-size:24px;font-weight:600">' + (key === 'del' ? '⌫' : key) + '</button>';
    }).join('');
    return bar('Sign in') + '<div class="pad" style="padding-top:30px"><div class="h1">Welcome back</div>' +
      '<div class="mu" style="font-size:15px;line-height:22px;margin-top:8px">Sign in to your real itunda account.</div>' +
      '<div style="margin-top:24px;padding:16px;border-radius:18px;background:var(--chip)"><div class="sm mu">Phone number</div><div style="font-size:18px;font-weight:800;margin-top:4px">' + normalizePhone(S.ob.ph).replace(/[<>&"]/g,'') + '</div></div>' +
      '<div style="text-align:center;margin-top:28px;font-size:14px;font-weight:700">Your 6-digit PIN</div><div style="display:flex;justify-content:center;padding:18px 0">' + dots + '</div>' +
      '<div id="web-login-error" role="alert" style="color:var(--er);min-height:20px;text-align:center;font-size:13px"></div>' +
      '<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:4px">' + keys + '</div>' +
      '<button class="btn" style="margin-top:18px" ' + (pin.length === 6 && !busy ? '' : 'disabled') + ' onclick="backendLogin()">Sign in securely</button>' +
      '<button class="btn sec2" style="margin-top:10px" onclick="S.obStep=\'obNotFound\';S.ob.pin=\'\';render(\'push\')">Create a new account</button></div>';
  };
  window.backendPinKey = function (key) {
    if (key === 'del') S.ob.pin = (S.ob.pin || '').slice(0, -1);
    else if (/^\\d$/.test(key) && (S.ob.pin || '').length < 6) S.ob.pin = (S.ob.pin || '') + key;
    render('fade-none');
  };
  window.backendLogin = async function () {
    if (busy || (S.ob.pin || '').length !== 6) return;
    busy = true; render('fade-none');
    try {
      const session = await api('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ phoneNumber: normalizePhone(S.ob.ph), password: S.ob.pin })
      }, false);
      if (!session.accessToken || !session.refreshToken || !session.user) throw new Error('The server returned an incomplete session.');
      localStorage.setItem(ACCESS_KEY, session.accessToken);
      localStorage.setItem(REFRESH_KEY, session.refreshToken);
      liveUser = session.user;
      await loadAccount();
      S.auth = true; S.obStep = null; S.stack = []; S.tab = 'home'; S.first = true;
      render('fade'); window.toast('Signed in to your real itunda account');
    } catch (error) {
      S.ob.pin = ''; render('fade-none');
      const errorNode = document.getElementById('web-login-error');
      if (errorNode) errorNode.textContent = error.message || 'Sign in failed. Check your phone number and PIN.';
    } finally { busy = false; }
  };
  window.obPhoneNext = function () { S.ob.pin = ''; S.obStep = 'backendLogin'; render('push'); };

  const previousBankScreen = screens.bank;
  screens.bank = function () {
    const html = previousBankScreen();
    const rows = liveTransactions.slice(0, 8).map(function (tx) {
      const label = String(tx.description || tx.title || tx.type || tx.reference || 'Account activity').replace(/[<>&"]/g, '');
      const amount = Number(tx.amount || 0);
      const credit = amount > 0 || String(tx.direction || '').toUpperCase() === 'CREDIT';
      const date = tx.createdAt || tx.created_at || tx.timestamp;
      return '<div class="row" style="cursor:default"><div class="ico">' + (credit ? '＋' : '−') + '</div>' +
        '<div style="min-width:0;flex:1"><div class="t">' + label + '</div><div class="s">' +
        (date ? new Date(date).toLocaleDateString().replace(/[<>&"]/g, '') : '') + '</div></div>' +
        '<div class="r" style="color:' + (credit ? 'var(--ok)' : 'var(--tx)') + '">' + (credit ? '+' : '') + fmt(Math.abs(amount)) + ' RWF</div></div>';
    }).join('');
    return html + '<div class="sec"><div class="sech">Real account activity</div>' +
      (rows || '<div class="fine" style="padding:12px 0">No account transactions yet.</div>') +
      '<div class="fine" style="padding-top:8px">Loaded from your itunda account ledger.</div></div>';
  };
  const previousProfileScreen = screens.profile;
  screens.profile = function () {
    let html = previousProfileScreen();
    if (liveUser) {
      const fullName = [liveUser.firstName, liveUser.lastName].filter(Boolean).join(' ').replace(/[<>&"]/g, '');
      html = html.replace('078 ••• 214', String(liveUser.phoneNumber || '').replace(/[<>&"]/g, ''));
      html = html.replace('Your details are sample data.', 'Your details are loaded from your itunda account.');
      html = html.replace('March 2025', liveUser.createdAt ? new Date(liveUser.createdAt).toLocaleDateString() : '—');
      if (fullName) html = html.replace('Amina Uwase', fullName);
    }
    return html;
  };

  (async function restoreSession() {
    if (!localStorage.getItem(ACCESS_KEY)) return;
    try {
      const saved = localStorage.getItem('itunda_web_user');
      if (saved) liveUser = JSON.parse(saved);
      await loadAccount();
      S.auth = true; S.obStep = null; S.stack = []; S.tab = 'home'; render('fade');
    } catch (error) {
      console.warn('[itunda backend] Could not restore session:', error.message);
    }
  })();
})();
