/* Real Itunda authentication and account data for the v104 consumer web experience. */
(function () {
  'use strict';
  const API_BASE = 'https://itunda-api-hptgu43hsa-du.a.run.app';
  const ACCESS_KEY = 'itunda_web_access_token';
  const REFRESH_KEY = 'itunda_web_refresh_token';
  let mainAccountId = null;
  let liveTransactions = [];
  let liveUser = null;
  let transactionLoadError = null;
  let profileLoadError = null;
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
    // Account data is required for sign-in. Secondary reads may fail independently,
    // but a failed request must never be represented as a successful empty result.
    const results = await Promise.all([
      api('/api/v1/account'),
      api('/api/v1/account/transactions').then(function (body) { return { ok: true, body: body }; })
        .catch(function (error) { return { ok: false, error: error }; }),
      api('/api/v1/auth/profile').then(function (body) { return { ok: true, body: body }; })
        .catch(function (error) { return { ok: false, error: error }; })
    ]);
    const accountList = Array.isArray(results[0].accounts) ? results[0].accounts : [];
    transactionLoadError = results[1].ok ? null : results[1].error;
    profileLoadError = results[2].ok ? null : results[2].error;
    if (results[1].ok) {
      liveTransactions = Array.isArray(results[1].body.transactions) ? results[1].body.transactions : [];
    }
    if (results[2].ok && results[2].body && results[2].body.user) liveUser = results[2].body.user;
    const main = accountList.find(function (a) { return String(a.type || '').toUpperCase() === 'MAIN'; }) || accountList[0];
    if (!main) throw new Error('No account balance is available for this user yet.');
    mainAccountId = main.id;
    S.bank = Number(main.balance || 0);
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
    if (errorNode) errorNode.textContent = 'Loading your account…';
    try {
      await loadAccount();
      S.auth = true; S.obStep = null; S.stack = []; S.tab = 'home'; S.first = true;
      render('fade');
      window.toast('Your account is ready');
    } catch (error) {
      showAccountLoadRetry('You are signed in, but account data is still unavailable. Please retry.');
      console.warn('[itunda backend] Account load after sign-in failed:', error.message);
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
    return html + '<div class="sec"><div class="sech">Real account activity</div>' +
      (transactionLoadError
        ? '<div class="fine" role="alert" style="padding:12px 0;color:var(--er)">Could not load account activity. ' + String(transactionLoadError.message || 'Please try again.').replace(/[<>&"]/g, '') + '</div>'
        : (rows || '<div class="fine" style="padding:12px 0">No account transactions yet.</div>')) +
      '<div class="fine" style="padding-top:8px">' + (transactionLoadError ? 'Activity could not be verified from the ledger.' : 'Loaded from your itunda account ledger.') + '</div></div>';
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
      html = html.replace('Your details are sample data.', profileLoadError
        ? 'Some profile details could not be refreshed from your account.'
        : 'Your details are loaded from your itunda account.');
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


  // Real bills/airtime backend integration. Provider names and IDs come from the
  // authenticated backend catalog; unsupported prototype-only billers are disabled.
  let billProviders = null;
  let billProvidersError = null;
  let billProvidersPromise = null;
  let billBusy = false;
  let billRequestFingerprint = null;
  let billRequestKey = null;
  let lastBillTransaction = null;
  const billProviderAliases = {
    'REG electricity': ['reg', 'electricity'],
    'WASAC water': ['wasac', 'water'],
    'MTN airtime': ['mtn', 'airtime'],
    'Airtel airtime': ['airtel', 'airtime'],
    'Irembo': ['irembo'],
    'RRA taxes': ['rra', 'income tax']
  };
  function loadBillProviders() {
    if (billProviders) return Promise.resolve(billProviders);
    if (billProvidersPromise) return billProvidersPromise;
    billProvidersPromise = api('/api/v1/bills/providers').then(function (body) {
      billProviders = Array.isArray(body.providers) ? body.providers.filter(function (p) { return p && p.isActive !== false; }) : [];
      if (!billProviders.length) throw new Error('No active bill providers are available right now.');
      billProvidersError = null;
      return billProviders;
    }).catch(function (error) {
      billProvidersError = error;
      throw error;
    }).finally(function () { billProvidersPromise = null; });
    return billProvidersPromise;
  }
  function providerForBill(label) {
    if (!Array.isArray(billProviders)) return null;
    const aliases = billProviderAliases[label] || [];
    return billProviders.find(function (p) {
      const name = String(p.name || '').toLowerCase();
      return aliases.length && aliases.every(function (word) { return name.includes(word); });
    }) || null;
  }
  function escapeBillText(value) {
    return String(value == null ? '' : value).replace(/[<>&"]/g, function (ch) {
      return ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' })[ch];
    });
  }
  const prototypeBillScreen = screens.bill;
  if (prototypeBillScreen) {
    screens.bill = function () {
      const html = prototypeBillScreen();
      if (!billProviders && !billProvidersPromise && !billProvidersError) {
        loadBillProviders().then(function () {
          if (S.auth && S.stack[S.stack.length - 1] === 'bill') render('fade-none');
        }).catch(function (error) {
          if (S.auth && S.stack[S.stack.length - 1] === 'bill') render('fade-none');
          console.warn('[itunda bills] Provider catalog unavailable:', error.message);
        });
      }
      const labels = Object.keys(billProviderAliases).filter(function (label) { return providerForBill(label); });
      const chips = labels.length
        ? labels.map(function (label) {
            return '<button class="cch ' + (S.bl.p === label ? 'on' : '') + '" onclick="billSet(\'' + label + '\')">' +
              escapeBillText(providerForBill(label).name) + '</button>';
          }).join('')
        : '<div class="fine" role="status" style="padding:12px 0">' +
          (billProvidersError ? 'Bill providers could not be loaded. Try again later.' : 'Loading available bill providers…') + '</div>';
      let out = html.replace(/<div class="chips" style="padding-top:16px">[\s\S]*?<\/div>/, '<div class="chips" style="padding-top:16px">' + chips + '</div>');
      out = out.replace('Providers and numbers are sample data. No real payment is made.',
        'Provider catalog is live. Itunda records the payment through its configured connector; external provider settlement is not independently verified.');
      if (billProvidersError) {
        out = out.replace(/<button id="bgo" class="btn"[^>]*>[\s\S]*?<\/button>/,
          '<button id="bgo" class="btn" disabled>Provider unavailable</button>');
      } else if (!providerForBill(S.bl.p)) {
        out = out.replace(/<button id="bgo" class="btn"[^>]*>[\s\S]*?<\/button>/,
          '<button id="bgo" class="btn" disabled>Service unavailable</button>');
      }
      return out;
    };
  }
  window.billPay = function () {
    if (billBusy) return;
    if (!localStorage.getItem(ACCESS_KEY) || !mainAccountId) return fail(new Error('Sign in before paying a bill.'));
    if (!billProviders || billProvidersError) return fail(new Error('The bill provider catalog is not available. Please try again.'));
    const bill = S.bl || {};
    const provider = providerForBill(bill.p);
    if (!provider) return fail(new Error('This bill provider is not supported by the live backend.'));
    if (!bill.num || !Number.isFinite(Number(bill.amt)) || Number(bill.amt) <= 0) {
      return fail(new Error('Enter a valid account or phone number and payment amount.'));
    }
    if (Number(bill.amt) > Number(S.bank)) return fail(new Error('Insufficient funds in your itunda account.'));
    $('#layer').innerHTML = '<div class="sheetbg" onclick="closeSheet()"></div><div class="sheet">' +
      '<div style="margin-bottom:12px">⚠️</div><div class="h1" style="font-size:22px;line-height:30px">Confirm payment</div>' +
      '<div class="mu" style="font-size:15px;margin:8px 0 24px">Pay ' + fmt(Number(bill.amt)) + ' RWF to ' +
      escapeBillText(provider.name) + ' · ' + escapeBillText(bill.num) +
      '. The backend will confirm whether it succeeds.</div><div class="row2"><button class="btn sec2" onclick="closeSheet()">Cancel</button>' +
      '<button class="btn" onclick="billDone()">Confirm payment</button></div></div>';
  };
  window.billDone = async function () {
    if (billBusy) return;
    if (!localStorage.getItem(ACCESS_KEY) || !mainAccountId) return fail(new Error('Sign in before paying a bill.'));
    const bill = S.bl || {};
    const provider = providerForBill(bill.p);
    if (!provider) return fail(new Error('This bill provider is not supported by the live backend.'));
    billBusy = true;
    try {
      const airtime = /airtime/i.test(bill.p);
      const rail = airtime ? (/airtel/i.test(bill.p) ? 'Airtel Money' : 'MTN Mobile Money') : provider.name;
      const payload = airtime
        ? { phoneNumber: normalizePhone(bill.num), amount: Number(bill.amt), provider: rail }
        : { billId: provider.id, amount: Number(bill.amt), accountNumber: bill.num, provider: rail };
      const fingerprint = JSON.stringify({ path: airtime ? '/api/v1/bills/airtime' : '/api/v1/bills/pay', payload: payload });
      if (!billRequestKey || billRequestFingerprint !== fingerprint) {
        billRequestKey = window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() :
          'bill-web-' + Date.now() + '-' + Math.random().toString(36).slice(2);
        billRequestFingerprint = fingerprint;
      }
      const idempotencyKey = billRequestKey;
      const result = await api(airtime ? '/api/v1/bills/airtime' : '/api/v1/bills/pay', {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(payload)
      });
      lastBillTransaction = result.transaction || null;
      if (!lastBillTransaction) throw new Error('The backend did not return a payment transaction. Check activity before retrying.');
      billRequestKey = null; billRequestFingerprint = null;
      closeSheet();
      await loadAccount().catch(function (refreshError) {
        console.warn('[itunda bills] Payment confirmed; account refresh failed:', refreshError.message);
      });
      S.bk = Number(lastBillTransaction.amount || bill.amt);
      S.stack = ['billdone'];
      render('push');
      window.toast(result.message || 'Payment confirmed by itunda');
    } catch (error) {
      closeSheet();
      fail(error);
    } finally { billBusy = false; }
  };
  screens.billdone = function () {
    if (!lastBillTransaction) {
      return bar('Payment status') + '<div class="pad" role="alert" style="padding-top:32px">No confirmed payment receipt is available. Check account activity before trying again.</div>';
    }
    const tx = lastBillTransaction;
    const amount = Number(tx.amount || S.bk || 0);
    const ref = escapeBillText(tx.referenceNumber || tx.id || '—');
    const label = escapeBillText(tx.description || tx.type || 'Bill payment');
    return '<div style="display:flex;flex-direction:column;align-items:center;padding-top:clamp(28px,12vh,190px)">' +
      '<div class="g3">' + gl('success3d', 132) + '</div><div class="h1" style="margin-top:28px;font-size:34px;line-height:42px">' +
      fmt(amount) + ' RWF</div><div class="mu" style="font-size:16px;margin-top:8px">' + label + '</div></div>' +
      '<div style="margin:44px 24px 0;padding:4px 20px;border-radius:20px;background:var(--chip)">' +
      '<div class="row" style="cursor:default"><span class="mu">Status</span><b>' + escapeBillText(tx.status || 'COMPLETED') + '</b></div>' +
      '<div class="row" style="cursor:default"><span class="mu">Reference</span><b>' + ref + '</b></div>' +
      '<div class="row" style="cursor:default"><span class="mu">Amount</span><b>' + fmt(amount) + ' RWF</b></div>' +
      '<div class="fine" style="padding:12px 0">Receipt supplied by the itunda backend. Provider settlement availability may vary.</div></div>' +
      '<div class="cta" style="border:0"><button class="btn" onclick="done()">Done</button></div>';
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
