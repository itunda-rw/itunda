/* Real Itunda authentication and account data for the v104 consumer web experience. */
(function () {
  'use strict';
  const API_BASE = 'https://itunda-api-hptgu43hsa-du.a.run.app';
  const ACCESS_KEY = 'itunda_web_access_token';
  const REFRESH_KEY = 'itunda_web_refresh_token';
  let mainAccountId = null;
  let liveTransactions = [];
  let transactionLoadError = null;
  let liveUser = null;
  let lastTransaction = null;
  let termsCatalog = [];
  let busy = false;
  let transferBusy = false;
  let refreshInFlight = null;

  function normalizePhone(value) {
    const v = String(value || '').replace(/[\s()-]/g, '');
    if (v.startsWith('+')) return v;
    if (v.startsWith('250') && v.length === 12) return '+' + v;
    if (v.startsWith('0') && v.length === 10) return '+250' + v.slice(1);
    if (/^\d{9}$/.test(v)) return '+250' + v;
    return v;
  }
  // Keep phone validation local to the auth bridge; the prototype's phOk helper is not in this script's scope.
  // Demo mode is explicitly opt-in via ?demo=1 and never creates an authenticated session.
  const demoMode = new URLSearchParams(window.location.search).get('demo') === '1';
  function phOk(value) {
    return /^\+\d{8,15}$/.test(normalizePhone(value));
  }
  async function api(path, options, retry) {
    options = options || {};
    if (demoMode) throw new Error('Demo mode uses sample data only. Exit demo mode and sign in to use real account services.');
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    const access = localStorage.getItem(ACCESS_KEY);
    if (access) headers.Authorization = 'Bearer ' + access;
    let response;
    try { response = await fetch(API_BASE + path, Object.assign({}, options, { headers })); }
    catch (_) { throw new Error('Could not reach itunda. Check your connection and try again.'); }
    if (response.status === 401 && retry !== false && access && path !== '/api/v1/auth/refresh') {
      if (!refreshInFlight) {
        refreshInFlight = (async function () {
          const refreshToken = localStorage.getItem(REFRESH_KEY);
          if (!refreshToken) return false;
          try {
            const refreshed = await fetch(API_BASE + '/api/v1/auth/refresh', {
              method: 'POST', headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ refreshToken: refreshToken })
            });
            if (!refreshed.ok) return false;
            const session = await refreshed.json();
            localStorage.setItem(ACCESS_KEY, session.accessToken);
            localStorage.setItem(REFRESH_KEY, session.refreshToken);
            return true;
          } catch (_) { return false; }
          finally { refreshInFlight = null; }
        })();
      }
      if (await refreshInFlight) return api(path, options, false);
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem('itunda_web_user');
      throw new Error('Your session expired. Please sign in again.');
    }
    const body = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(body.message || body.error || ('Request failed (' + response.status + ').'));
    return body;
  }
  async function loadAccount() {
    const results = await Promise.all([
      api('/api/v1/account'),
      api('/api/v1/account/transactions').then(function (body) {
        if (!Array.isArray(body.transactions)) throw new Error('Your account transaction history could not be read.');
        transactionLoadError = null;
        return body;
      }).catch(function (error) {
        transactionLoadError = error.message || 'Transaction history is temporarily unavailable.';
        console.warn('[itunda backend] Transaction history unavailable:', transactionLoadError);
        return null;
      }),
      api('/api/v1/auth/profile').catch(function () { return null; })
    ]);
    const accountList = Array.isArray(results[0].accounts) ? results[0].accounts : [];
    liveTransactions = results[1] ? results[1].transactions : [];
    if (results[2] && results[2].user) liveUser = results[2].user;
    const main = accountList.find(function (a) { return String(a.type || '').toUpperCase() === 'MAIN'; }) || accountList[0];
    if (!main || main.id === undefined || main.id === null || main.id === '') throw new Error('No account is available for this user yet.');
    const balance = main.balance;
    if (balance === undefined || balance === null || balance === '' || !Number.isFinite(Number(balance))) {
      throw new Error('Your account balance is unavailable. Please retry; it has not been replaced with a sample value.');
    }
    mainAccountId = main.id;
    S.bank = Number(balance);
    S.name = [liveUser && liveUser.firstName, liveUser && liveUser.lastName].filter(Boolean).join(' ') || S.name || '';
    if (Array.isArray(S.act)) {
      S.act = liveTransactions.map(function (tx) {
        const outgoing = Boolean((liveUser && tx.senderId === liveUser.id) || tx.fromAccountId === mainAccountId);
        const amount = Number(tx.amount || 0) * (outgoing ? -1 : 1);
        const type = String(tx.type || '').toUpperCase();
        const icon = type === 'INTEREST' ? 'coin' : (type === 'BILL' || type === 'AIRTIME' || type === 'PAYMENT' ? 'bill' : (outgoing ? 'send' : 'up'));
        const date = tx.createdAt ? new Date(tx.createdAt) : new Date();
        const status = String(tx.status || 'COMPLETED').toLowerCase();
        const label = outgoing ? 'Paid' : 'Received';
        return [icon, String(tx.description || tx.type || 'Account activity'), label + ' · ' + date.toLocaleDateString(), amount, status, date.getTime()];
      });
    }
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
      '<button class="btn sec2" style="margin-top:10px" onclick="S.ob.name=\'\';S.ob.first=\'\';S.ob.last=\'\';S.ob.pin=\'\';S.obStep=\'obName\';render(\'push\')">Create a new account</button></div>';
  };
  window.backendPinKey = function (key) {
    if (key === 'del') S.ob.pin = (S.ob.pin || '').slice(0, -1);
    else if (/^\d$/.test(key) && (S.ob.pin || '').length < 6) S.ob.pin = (S.ob.pin || '') + key;
    render('fade-none');
  };
  function showAccountLoadRetry(message) {
    const errorNode = document.getElementById('web-login-error');
    if (!errorNode) return;
    errorNode.textContent = message;
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.className = 'chip';
    retry.style.marginTop = '8px';
    retry.textContent = 'Retry loading account';
    retry.onclick = window.backendRetryAccount;
    errorNode.appendChild(document.createElement('br'));
    errorNode.appendChild(retry);
  };
  window.backendRetryAccount = async function () {
    if (busy) return;
    busy = true;
    const errorNode = document.getElementById('web-login-error');
    const fromLogin = Boolean(errorNode);
    if (errorNode) errorNode.textContent = 'Loading your account…';
    try {
      await loadAccount();
      if (fromLogin) {
        S.auth = true; S.obStep = null; S.stack = []; S.tab = 'home'; S.first = true;
        render('fade');
      } else {
        render('fade-none');
      }
      window.toast(transactionLoadError ? 'Account loaded; transaction history is still unavailable' : 'Your account is up to date');
    } catch (error) {
      if (fromLogin) {
        showAccountLoadRetry('You are signed in, but account data is still unavailable. Please retry.');
      } else {
        fail(new Error('Account data could not be refreshed. Your displayed balance has not been replaced. ' + (error.message || 'Please try again.')));
      }
      console.warn('[itunda backend] Account refresh failed:', error.message);
    } finally { busy = false; }
  };
  window.backendLogin = async function () {
    if (busy || (S.ob.pin || '').length !== 6) return;
    busy = true; render('fade-none');
    try {
      let session;
      // Keep credential/authentication failures separate from post-login account loading.
      try {
        session = await api('/api/v1/auth/login', {
          method: 'POST',
          body: JSON.stringify({ phoneNumber: normalizePhone(S.ob.ph), password: S.ob.pin })
        }, false);
        if (!session.accessToken || !session.refreshToken || !session.user) throw new Error('The server returned an incomplete session.');
      } catch (error) {
        S.ob.pin = ''; render('fade-none');
        const errorNode = document.getElementById('web-login-error');
        if (errorNode) errorNode.textContent = error.message || 'Sign in failed. Check your phone number and PIN.';
        return;
      }
      // Persist the real session before loading account data. If the account API is
      // temporarily unavailable, keep the valid session and let the user retry without
      // repeatedly entering their PIN or confusing a data-load failure with bad login.
      localStorage.setItem(ACCESS_KEY, session.accessToken);
      localStorage.setItem(REFRESH_KEY, session.refreshToken);
      liveUser = session.user;
      localStorage.setItem('itunda_web_user', JSON.stringify(session.user));
      try {
        await loadAccount();
      } catch (error) {
        S.ob.pin = ''; render('fade-none');
        showAccountLoadRetry('You are signed in, but your account data could not be loaded. Retry without entering your PIN again.');
        console.warn('[itunda backend] Account load after sign-in failed:', error.message);
        return;
      }
      S.auth = true; S.obStep = null; S.stack = []; S.tab = 'home'; S.first = true;
      render('fade'); window.toast('Signed in to your real itunda account');
    } finally { busy = false; }
  };
  window.obPhoneNext = function () { if (!phOk(S.ob.ph)) { fail(new Error('Enter a valid phone number first.')); return; } S.ob.pin = ''; S.obStep = 'backendLogin'; render('push'); };
  window.obNameNext = function () { if (String(S.ob.name || '').trim().length > 1) window.obGo('obTerms'); };
  window.obSkip = function () {
    if (!demoMode) return fail(new Error('Please sign in or create an account to continue.'));
    // UI-only entry: no token is minted, no user session is persisted, and API calls are disabled.
    S.auth = true; S.obStep = null; S.stack = []; S.tab = 'home'; S.first = true;
    render('fade');
    if (typeof window.toast === 'function') window.toast('Demo mode — sample data only');
  };

  const previousObBack = window.obBack;
  window.obBack = function () {
    if (S.obStep === 'obTerms') { S.obStep = 'obName'; render('pop'); return; }
    if (S.obStep === 'backendLogin') { S.obStep = 'obPhone'; render('pop'); return; }
    previousObBack();
  };

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
    if (demoMode) return previousBankScreen() + '<div class="sec"><div class="sech">Demo account activity</div><div class="fine" style="padding:12px 0">Sample data only. This demo is not connected to a real account or ledger.</div></div>';
    const activityState = transactionLoadError
      ? '<div class="fine" role="status" style="padding:12px 0">Transaction history could not be loaded. Your account balance is unchanged. ' + String(transactionLoadError).replace(/[<>&"]/g, '') + '</div><button class="chip" onclick="backendRetryAccount()">Retry loading account</button>'
      : (rows || '<div class="fine" style="padding:12px 0">No account transactions yet.</div>');
    return html + '<div class="sec"><div class="sech">Real account activity</div>' +
      activityState +
      '<div class="fine" style="padding-top:8px">Loaded from your itunda account ledger.</div></div>';
  };
  const previousSentScreen = screens.sent;
  screens.sent = function () {
    let html = previousSentScreen();
    const reference = lastTransaction && lastTransaction.referenceNumber ? String(lastTransaction.referenceNumber) : '—';
    return html.replace('IT-20481-7', reference.replace(/[<>&"]/g, ''));
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

  function termKey(id) {
    if (id === 'terms_of_service') return 't';
    if (id === 'privacy_policy') return 'p';
    if (id === 'marketing_communications') return 'm';
    return 'term_' + id;
  }
  function escapeTerm(value) {
    return String(value || '').replace(/[<>&"]/g, function (ch) {
      return ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' })[ch];
    });
  }
  const fallbackTerms = [
    { id: 'terms_of_service', title: 'Terms of Service', required: true, summary: '' },
    { id: 'privacy_policy', title: 'Privacy Policy', required: true, summary: '' },
    { id: 'marketing_communications', title: 'Marketing Communications', required: false, summary: '' }
  ];
  screens.obTerms = function () {
    const docs = termsCatalog.length ? termsCatalog : fallbackTerms;
    const rows = docs.map(function (doc) {
      const key = termKey(doc.id), checked = Boolean(S.ob[key]);
      const marker = doc.required ? t('o_req') : (({ rw: 'Bihitamo', fr: 'Facultatif' })[S.lang] || 'Optional');
      return '<div class="row tap" role="button" tabindex="0" onclick="tcTog(\'' + key + '\')">' +
        '<div class="cb ' + (checked ? 'on' : '') + '">' + (checked ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#121216" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5L19 7"/></svg>' : '') + '</div>' +
        '<div style="flex:1;min-width:0"><div class="t" style="font-size:15px">' + escapeTerm(doc.title) + '</div>' +
        (doc.summary ? '<div class="s" style="line-height:18px">' + escapeTerm(doc.summary) + '</div>' : '') + '</div>' +
        '<span class="sm" style="color:' + (doc.required ? 'var(--lk)' : 'var(--mu)') + ';font-weight:700">' + marker + '</span></div>';
    }).join('');
    const all = docs.length > 0 && docs.every(function (doc) { return Boolean(S.ob[termKey(doc.id)]); });
    const valid = docs.filter(function (doc) { return doc.required; }).every(function (doc) { return Boolean(S.ob[termKey(doc.id)]); });
    return obbar() + '<div class="pad" style="padding-top:16px"><div class="h1">' + t('o_terms_h') + '</div><div class="mu" style="font-size:16px;margin-top:8px">' + t('o_terms_s') + '</div></div>' +
      '<div class="pad" style="padding-top:22px"><div class="row tap" role="button" tabindex="0" style="background:var(--chip);border-radius:16px;padding:14px 16px" onclick="tcAll()">' +
      '<div class="cb ' + (all ? 'on' : '') + '">' + (all ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#121216" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5L19 7"/></svg>' : '') + '</div>' +
      '<div class="t" style="font-size:16px">' + t('o_all') + '</div></div><div style="padding-top:8px">' + rows + '</div></div>' +
      '<div class="cta"><button id="obnext" class="btn" ' + (valid ? '' : 'disabled') + ' onclick="obGo(\'obPin\')">' + t('nx') + '</button></div>';
  };
  const previousObGo = window.obGo;
  window.obGo = function (step) {
    if (step === 'obTerms') {
      api('/api/v1/auth/terms', {}, false).then(function (body) {
        termsCatalog = Array.isArray(body.terms) ? body.terms : [];
        if (S.obStep === 'obTerms') render('fade-none');
      }).catch(function (error) {
        console.warn('[itunda backend] Terms catalog unavailable:', error.message);
      });
    }
    previousObGo(step);
  };
  window.tcAll = function () {
    const docs = termsCatalog.length ? termsCatalog : fallbackTerms;
    const all = docs.length > 0 && docs.every(function (doc) { return Boolean(S.ob[termKey(doc.id)]); });
    docs.forEach(function (doc) { S.ob[termKey(doc.id)] = !all; });
    render('fade-none');
  };
  window.tcTog = function (key) { S.ob[key] = !S.ob[key]; render('fade-none'); };

  // Real registration: the required consent IDs come from the backend catalog.
  window.obCreate = async function () {
    if (busy) return;
    busy = true;
    try {
      const catalog = await api('/api/v1/auth/terms', {}, false);
      const currentTerms = Array.isArray(catalog.terms) ? catalog.terms : [];
      if (!currentTerms.length) throw new Error('Required terms could not be loaded. Please try again later.');
      termsCatalog = currentTerms;
      const acceptedTermsIds = currentTerms.filter(function (doc) { return Boolean(S.ob[termKey(doc.id)]); }).map(function (doc) { return doc.id; });
      const missing = currentTerms.filter(function (doc) { return doc.required && acceptedTermsIds.indexOf(doc.id) < 0; });
      const enteredName = String(S.ob.name || '').trim();
      const nameParts = enteredName ? enteredName.split(/\s+/) : [];
      const firstName = nameParts.length ? nameParts[0] : String(S.ob.first || '').trim();
      const lastName = String(S.ob.last || '').trim() || nameParts.slice(1).join(' ') || firstName;
      if (missing.length) throw new Error('Please accept each required term before creating your account.');
      const session = await api('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          phoneNumber: normalizePhone(S.ob.ph), email: null,
          firstName: firstName,
          lastName: lastName,
          password: S.ob.pin, acceptedTermsIds: acceptedTermsIds
        })
      }, false);
      if (!session.accessToken || !session.refreshToken || !session.user) throw new Error('The server returned an incomplete session.');
      localStorage.setItem(ACCESS_KEY, session.accessToken);
      localStorage.setItem(REFRESH_KEY, session.refreshToken);
      localStorage.setItem('itunda_web_user', JSON.stringify(session.user));
      liveUser = session.user;
      await loadAccount();
      S.auth = true; S.obStep = null; S.stack = []; S.tab = 'home'; S.first = true;
      render('fade'); window.toast('Your itunda account is ready');
    } catch (error) {
      S.ob.err = error.message || 'We could not create your account.';
      render('fade-none'); fail(error);
    } finally { busy = false; }
  };

  // Resolve the real recipient before the confirmation sheet can authorize a transfer.
  // Keep the API host aligned with the Cloud Run deployment workflow.
  let recipientPreview = null;
  let resolvingRecipient = false;
  function transferRecipientIdentifier(value) {
    const raw = String(value || '').replace(/[\s()-]/g, '');
    return /^\d{10,20}$/.test(raw) && !raw.startsWith('0') && !raw.startsWith('250')
      ? raw : normalizePhone(raw);
  }
  const prototypeConfirmTransfer = window.confirm_;
  window.confirm_ = async function () {
    if (resolvingRecipient) return;
    const identifier = transferRecipientIdentifier(S.to);
    if (!identifier) return fail(new Error('Choose a recipient before continuing.'));
    resolvingRecipient = true;
    try {
      const data = await api('/api/v1/p2p/recipient?identifier=' + encodeURIComponent(identifier));
      const recipient = data && data.recipient;
      if (!recipient || !recipient.recipientUserId || !String(recipient.displayName || '').trim()) {
        throw new Error('We could not verify this recipient. Check the number and try again.');
      }
      recipientPreview = { identifier: identifier, displayName: String(recipient.displayName).trim(), at: Date.now() };
      const phoneOrAccount = S.to;
      S.to = recipientPreview.displayName;
      try { prototypeConfirmTransfer.call(window); }
      finally { S.to = phoneOrAccount; }
    } catch (error) {
      recipientPreview = null;
      fail(error);
    } finally { resolvingRecipient = false; }
  };

  // Money moves only after a real recipient preview and an idempotent backend send.
  window.finish = async function () {
    if (transferBusy) return;
    const amount = Number(S.amt);
    const recipient = transferRecipientIdentifier(S.to);
    const validRecipient = /^\+\d{8,15}$/.test(recipient) || /^\d{10,20}$/.test(recipient);
    if (!localStorage.getItem(ACCESS_KEY) || !mainAccountId) return fail(new Error('Sign in before sending money.'));
    if (!Number.isFinite(amount) || amount <= 0 || !validRecipient) {
      return fail(new Error('Enter a valid amount and the recipient’s phone number or account number.'));
    }
    if (!recipientPreview || recipientPreview.identifier !== recipient || Date.now() - recipientPreview.at > 120000) {
      return fail(new Error('Verify the recipient name before sending money.'));
    }
    transferBusy = true;
    try {
      const idempotencyKey = window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() :
        'web-' + Date.now() + '-' + Math.random().toString(36).slice(2);
      const result = await api('/api/v1/p2p/send', {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ recipient: recipient, amount: amount, description: 'Transfer from itunda web app', fromAccountId: mainAccountId })
      });
      S.sentAmt = amount;
      lastTransaction = result.transaction || null;
      if (result.newBalance !== undefined) S.bank = Number(result.newBalance);
      // Confirmation is the commit point. A later refresh failure must not make a
      // completed transfer look unsuccessful to the customer.
      await loadAccount().catch(function (refreshError) {
        console.warn('[itunda backend] Transfer succeeded; account refresh will retry later:', refreshError.message);
      });
      closeSheet(); S.stack = ['sent']; render('push');
      window.toast(result.message || 'Transfer completed by itunda');
    } catch (error) { fail(error); }
    finally { transferBusy = false; }
  };

  const oldLogOut = window.logOut;
  window.logOut = function () {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    if (localStorage.getItem(ACCESS_KEY)) {
      api('/api/v1/auth/logout', {
        method: 'POST', body: JSON.stringify(refreshToken ? { refreshToken: refreshToken } : {})
      }).catch(function () {});
    }
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem('itunda_web_user');
    liveUser = null; mainAccountId = null; liveTransactions = [];
    if (oldLogOut) oldLogOut();
  };

  // Real Maps API adapter for the v266 shell. Uses the existing authenticated API
  // helper and the backend's established response contracts; never substitutes demo
  // places when the service fails. Prototype screens can call these methods directly.
  window.itundaMaps = Object.freeze({
    searchPlaces: async function (query) {
      const q = String(query || '').trim();
      if (q.length < 2) return [];
      const body = await api('/api/v1/maps/search?q=' + encodeURIComponent(q));
      if (!Array.isArray(body.results)) throw new Error('Maps search returned an invalid response.');
      return body.results.map(function (place) {
        const latitude = Number(place.latitude);
        const longitude = Number(place.longitude);
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
        return {
          displayName: String(place.displayName || place.display_name || ''),
          latitude: latitude,
          longitude: longitude
        };
      }).filter(function (place) { return place && place.displayName; });
    },
    fetchCategories: async function () {
      const body = await api('/api/v1/maps/categories');
      if (!Array.isArray(body.categories)) throw new Error('Maps categories returned an invalid response.');
      return body.categories;
    },
    searchNearby: async function (category, latitude, longitude, radiusKm) {
      const lat = Number(latitude), lng = Number(longitude);
      const radius = radiusKm === undefined ? 2 : Number(radiusKm);
      if (!String(category || '').trim() || !Number.isFinite(lat) || !Number.isFinite(lng) ||
          !Number.isFinite(radius) || radius <= 0 || radius > 50) {
        throw new Error('Choose a place category and a valid map location.');
      }
      const query = '/api/v1/maps/nearby?category=' + encodeURIComponent(String(category)) +
        '&lat=' + encodeURIComponent(String(lat)) + '&lng=' + encodeURIComponent(String(lng)) +
        '&radiusKm=' + encodeURIComponent(String(radius));
      const body = await api(query);
      if (!Array.isArray(body.places)) throw new Error('Nearby places returned an invalid response.');
      return body.places;
    },
    getDirections: async function (fromLatitude, fromLongitude, toLatitude, toLongitude, mode) {
      const values = [fromLatitude, fromLongitude, toLatitude, toLongitude].map(Number);
      if (!values.every(Number.isFinite) ||
          values[0] < -90 || values[0] > 90 || values[2] < -90 || values[2] > 90 ||
          values[1] < -180 || values[1] > 180 || values[3] < -180 || values[3] > 180) {
        throw new Error('Choose a valid origin and destination.');
      }
      const travelMode = mode || 'DRIVING';
      if (!['DRIVING', 'WALKING', 'BIKING'].includes(travelMode)) throw new Error('Unsupported travel mode.');
      // Match maps-mfe's established backend contract: directions/alternatives returns
      // { success, routes: RouteResult[] }, with geometry, distance, duration and steps.
      const path = '/api/v1/maps/directions/alternatives?fromLat=' + encodeURIComponent(String(values[0])) +
        '&fromLng=' + encodeURIComponent(String(values[1])) + '&toLat=' + encodeURIComponent(String(values[2])) +
        '&toLng=' + encodeURIComponent(String(values[3])) + '&mode=' + encodeURIComponent(travelMode);
      const body = await api(path);
      if (!Array.isArray(body.routes)) throw new Error('Directions returned an invalid response.');
      return body.routes;
    }
  });

  // Real nearby results now drive the existing prototype map pins and category chips.
  const mapCategoryByChip = {
    all: 'RESTAURANT', cafe: 'CAFE', food: 'RESTAURANT', bus: 'BUS_STOP',
    pharm: 'PHARMACY', atm: 'ATM'
  };
  const mapChipByCategory = {
    RESTAURANT: 'food', CAFE: 'cafe', BUS_STOP: 'bus', PHARMACY: 'pharm', ATM: 'atm'
  };
  let nearbySequence = 0;
  function mapCoordinateToCanvas(latitude, longitude, places) {
    const centerLat = -1.9441, centerLng = 30.0619;
    const points = places.map(function (p) { return [Number(p.latitude), Number(p.longitude)]; })
      .filter(function (p) { return Number.isFinite(p[0]) && Number.isFinite(p[1]); });
    const lats = points.map(function (p) { return p[0]; });
    const lngs = points.map(function (p) { return p[1]; });
    const minLat = Math.min(centerLat - 0.02, ...(lats.length ? lats : [centerLat]));
    const maxLat = Math.max(centerLat + 0.02, ...(lats.length ? lats : [centerLat]));
    const minLng = Math.min(centerLng - 0.025, ...(lngs.length ? lngs : [centerLng]));
    const maxLng = Math.max(centerLng + 0.025, ...(lngs.length ? lngs : [centerLng]));
    return {
      x: 20 + ((Number(longitude) - minLng) / Math.max(0.00001, maxLng - minLng)) * 300,
      y: 20 + ((maxLat - Number(latitude)) / Math.max(0.00001, maxLat - minLat)) * 440
    };
  }
  function replacePrototypePois(places, category) {
    if (!Array.isArray(window.POI)) return;
    const usable = places.filter(function (p) {
      return p && String(p.displayName || '').trim() &&
        Number.isFinite(Number(p.latitude)) && Number.isFinite(Number(p.longitude));
    });
    const coords = usable.map(function (p) { return mapCoordinateToCanvas(p.latitude, p.longitude, usable); });
    window.POI.splice(0, window.POI.length);
    usable.forEach(function (place, index) {
      const pos = coords[index];
      const id = 'real-map-place-' + index;
      const name = String(place.displayName);
      window.POI.push({
        id: id, c: category || 'food', e: '📍', n: name,
        x: Math.max(16, Math.min(324, pos.x)), y: Math.max(20, Math.min(460, pos.y)),
        k: String(place.distanceKm == null ? 'Nearby place' : Number(place.distanceKm).toFixed(1) + ' km away'),
        r: '', d: place.distanceKm == null ? '' : Number(place.distanceKm).toFixed(1) + ' km',
        o: '', latitude: Number(place.latitude), longitude: Number(place.longitude)
      });
    });
  }
  async function loadRealNearby(categoryChip) {
    const sequence = ++nearbySequence;
    const category = mapCategoryByChip[categoryChip] || 'RESTAURANT';
    const canvas = document.querySelector('.screen');
    try {
      const places = await window.itundaMaps.searchNearby(category, -1.9441, 30.0619, 5);
      if (sequence !== nearbySequence || !Array.isArray(window.POI)) return;
      replacePrototypePois(places, mapChipByCategory[category] || categoryChip);
      if (typeof window.render === 'function') window.render('fade-none');
    } catch (error) {
      if (sequence !== nearbySequence) return;
      if (Array.isArray(window.POI)) window.POI.splice(0, window.POI.length);
      if (typeof window.toast === 'function') window.toast('Nearby places unavailable: ' + (error.message || 'try again'));
      if (typeof window.render === 'function') window.render('fade-none');
    }
  }
  if (typeof window.mpCat === 'function') {
    window.mpCat = function (chip) {
      M.cat = chip;
      M.sel = null;
      loadRealNearby(chip);
    };
  }
  if (typeof window.openMaps === 'function') {
    const openMapsWithLivePlaces = window.openMaps;
    window.openMaps = function () {
      openMapsWithLivePlaces();
      loadRealNearby(M && M.cat && M.cat !== 'all' ? M.cat : 'all');
    };
  }

  // Replace prototype-only POI matching with real backend Maps search results.
  let mapsSearchSequence = 0;
  let mapsSearchResults = [];
  window.itundaMapsChoosePlace = function (index) {
    const place = mapsSearchResults[index];
    if (!place) return;
    window.itundaMapsSelectedPlace = Object.freeze({
      displayName: place.displayName, latitude: place.latitude, longitude: place.longitude
    });
    if (M) M.realPlace = window.itundaMapsSelectedPlace;
    if (PLACE) { PLACE.n = place.displayName; PLACE.addr = place.displayName; }
    if (typeof window.toast === 'function') window.toast(place.displayName + ' · ' + place.latitude.toFixed(5) + ', ' + place.longitude.toFixed(5));
    if (typeof window.mpGo === 'function') window.mpGo('route');
  };
  if (typeof window.mpQ === 'function') {
    window.mpQ = async function (value) {
      const query = String(value || '').trim();
      const sequence = ++mapsSearchSequence;
      const root = document.getElementById('mpres');
      const recent = document.getElementById('mprec');
      if (!root) return;
      if (recent) recent.style.display = query ? 'none' : '';
      root.replaceChildren();
      mapsSearchResults = [];
      if (query.length < 2) return;
      const status = document.createElement('div');
      status.className = 'pad mu';
      status.setAttribute('role', 'status');
      status.textContent = 'Searching real places…';
      root.appendChild(status);
      try {
        const results = await window.itundaMaps.searchPlaces(query);
        if (sequence !== mapsSearchSequence || !document.getElementById('mpres')) return;
        mapsSearchResults = results;
        root.replaceChildren();
        if (!results.length) {
          status.textContent = 'No real places matched this search.';
          root.appendChild(status);
          return;
        }
        results.forEach(function (place, index) {
          const row = document.createElement('button');
          row.type = 'button';
          row.className = 'pl tap';
          row.style.cssText = 'width:100%;text-align:left;border:0;background:transparent;color:var(--tx);cursor:pointer';
          const pin = document.createElement('i');
          pin.style.fontStyle = 'normal';
          pin.textContent = '📍';
          const details = document.createElement('div');
          details.style.cssText = 'flex:1;min-width:0';
          const name = document.createElement('b');
          name.style.cssText = 'display:block;font-size:16px;font-weight:600';
          name.textContent = place.displayName;
          const coordinates = document.createElement('span');
          coordinates.textContent = place.latitude.toFixed(5) + ', ' + place.longitude.toFixed(5);
          details.append(name, coordinates);
          row.append(pin, details);
          row.addEventListener('click', function () { window.itundaMapsChoosePlace(index); });
          root.appendChild(row);
        });
      } catch (error) {
        if (sequence !== mapsSearchSequence || !document.getElementById('mpres')) return;
        root.replaceChildren();
        const message = document.createElement('div');
        message.className = 'pad mu';
        message.setAttribute('role', 'alert');
        message.textContent = error.message || 'Maps search is temporarily unavailable.';
        const retry = document.createElement('button');
        retry.type = 'button';
        retry.className = 'chip';
        retry.textContent = 'Retry search';
        retry.addEventListener('click', function () {
          const input = document.getElementById('mpq');
          window.mpQ(input ? input.value : query);
        });
        message.appendChild(retry);
        root.appendChild(message);
      }
    };
  }

  // Render actual OSRM route geometry and metrics on the prototype's existing route screen.
  let routeRequestKey = '';
  let routeResultCache = null;
  let routePendingKey = '';
  function routePathFromGeometry(geometry) {
    if (!Array.isArray(geometry) || geometry.length < 2) return '';
    const pts = geometry.map(function (pair) { return [Number(pair[0]), Number(pair[1])]; })
      .filter(function (p) { return Number.isFinite(p[0]) && Number.isFinite(p[1]); });
    if (pts.length < 2) return '';
    const minLat = Math.min.apply(null, pts.map(function (p) { return p[0]; }));
    const maxLat = Math.max.apply(null, pts.map(function (p) { return p[0]; }));
    const minLng = Math.min.apply(null, pts.map(function (p) { return p[1]; }));
    const maxLng = Math.max.apply(null, pts.map(function (p) { return p[1]; }));
    return pts.map(function (p, i) {
      const x = 18 + ((p[1] - minLng) / Math.max(0.000001, maxLng - minLng)) * 304;
      const y = 18 + ((maxLat - p[0]) / Math.max(0.000001, maxLat - minLat)) * 294;
      return (i ? 'L' : 'M') + x.toFixed(1) + ' ' + y.toFixed(1);
    }).join(' ');
  }
  function requestRealRoute(place, mode) {
    const key = [place.latitude, place.longitude, mode].join(':');
    if (routeRequestKey === key || routePendingKey === key) return;
    routePendingKey = key;
    window.itundaMaps.getDirections(-1.9441, 30.0619, place.latitude, place.longitude, mode)
      .then(function (routes) {
        if (routePendingKey !== key) return;
        routePendingKey = '';
        if (!routes.length) throw new Error('No route was returned for this destination.');
        routeRequestKey = key;
        routeResultCache = routes[0];
        if (typeof window.render === 'function') window.render('fade-none');
      }).catch(function (error) {
        if (routePendingKey === key) routePendingKey = '';
        if (typeof window.toast === 'function') window.toast('Directions unavailable: ' + (error.message || 'try again'));
      });
  }
  if (screens && typeof screens.route === 'function') {
    const prototypeRouteScreen = screens.route;
    screens.route = function () {
      const html = prototypeRouteScreen();
      const place = window.itundaMapsSelectedPlace || (M && M.realPlace);
      if (!place) return html;
      const mode = ({ car: 'DRIVING', walk: 'WALKING', bike: 'BIKING' })[M && M.mode] || 'DRIVING';
      requestRealRoute(place, mode);
      if (!routeResultCache || routeRequestKey !== [place.latitude, place.longitude, mode].join(':')) {
        return html.replace('aria-label="Route map"', 'aria-label="Route map; loading real directions"');
      }
      const path = routePathFromGeometry(routeResultCache.geometry);
      let output = html;
      if (path) output = output.replace(/<path d="[^"]+" fill="none" stroke="[^"]+" stroke-width="7"/, '<path d="' + path + '" fill="none" stroke="var(--lk)" stroke-width="7"');
      output = output.replace('2:06 pm – 2:41 pm', Math.round(routeResultCache.durationMinutes) + ' min')
        .replace('11 stops · 22 min', Math.round(routeResultCache.distanceKm * 10) / 10 + ' km · ' + Math.round(routeResultCache.durationMinutes) + ' min');
      return output;
    };
  }
  if (typeof window.mpOpenPoi === 'function') {
    window.mpOpenPoi = function (id) {
      const place = Array.isArray(window.POI) ? window.POI.find(function (p) { return p.id === id; }) : null;
      if (!place || !Number.isFinite(Number(place.latitude)) || !Number.isFinite(Number(place.longitude))) {
        if (typeof window.toast === 'function') window.toast('This place has no verified coordinates yet.');
        return;
      }
      window.itundaMapsSelectedPlace = Object.freeze({
        displayName: place.n, latitude: Number(place.latitude), longitude: Number(place.longitude)
      });
      if (M) M.realPlace = window.itundaMapsSelectedPlace;
      if (typeof window.mpGo === 'function') window.mpGo('route');
    };
  }

  (async function restoreSession() {
    if (demoMode || !localStorage.getItem(ACCESS_KEY)) return;
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
