/* Real Itunda authentication and account data for the v104 consumer web experience. */
(function () {
  'use strict';
  const API_BASE = 'https://itunda-api-hptgu43hsa-du.a.run.app';
  const ACCESS_KEY = 'itunda_web_access_token';
  const REFRESH_KEY = 'itunda_web_refresh_token';
  let mainAccountId = null;
  let liveTransactions = [];
  let transactionHistoryUnavailable = false;
  let liveAutoTransferCount = null;
  let liveUser = null;
  let lastTransaction = null;
  let termsCatalog = [];
  let busy = false;
  let transferBusy = false;
  // Preserve one idempotency key across retries of the same transfer intent.
  let transferIdempotencyKey = null;
  let transferIntentFingerprint = null;
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
            if (!session || typeof session.accessToken !== 'string' || !session.accessToken.trim() ||
                typeof session.refreshToken !== 'string' || !session.refreshToken.trim()) {
              console.warn('[itunda backend] Token refresh returned an incomplete session.');
              return false;
            }
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
    // Account availability must not depend on the transaction-history service.
    // Keep history failures explicit so the UI never turns an API error into an empty ledger.
    const results = await Promise.all([
      api('/api/v1/account'),
      api('/api/v1/account/transactions').catch(function (error) {
        console.warn('[itunda backend] Transaction history unavailable:', error.message);
        return null;
      }),
      api('/api/v1/auth/profile').catch(function () { return null; }),
      api('/api/v1/p2p/auto-transfers').catch(function () { return null; })
    ]);
    const accountList = Array.isArray(results[0].accounts) ? results[0].accounts : [];
    const transactionResponse = results[1];
    transactionHistoryUnavailable = !transactionResponse || !Array.isArray(transactionResponse.transactions);
    liveTransactions = transactionHistoryUnavailable ? [] : transactionResponse.transactions;
    if (results[2] && results[2].user) liveUser = results[2].user;
    const autoTransfers = results[3] && results[3].autoTransfers;
    liveAutoTransferCount = Array.isArray(autoTransfers)
      ? autoTransfers.filter(function (transfer) { return String(transfer.status || '').toUpperCase() === 'ACTIVE'; }).length
      : null;
    const main = accountList.find(function (a) { return String(a.type || '').toUpperCase() === 'MAIN'; }) || accountList[0];
    if (!main) throw new Error('No account balance is available for this user yet.');
    const balanceValue = main.balance;
    if (balanceValue === null || balanceValue === undefined || balanceValue === '' || !Number.isFinite(Number(balanceValue))) {
      throw new Error('Account balance is unavailable. Please retry.');
    }
    mainAccountId = main.id;
    S.bank = Number(balanceValue);
    S.name = [liveUser && liveUser.firstName, liveUser && liveUser.lastName].filter(Boolean).join(' ') || String(liveUser && liveUser.name || '').trim();
    if (Array.isArray(S.act)) {
      // The prototype's compact activity rows require a numeric amount. Incomplete
      // ledger records stay visible in the dedicated bank activity list, where their
      // amount/date can be explicitly labeled unavailable instead of fabricated here.
      S.act = liveTransactions.filter(function (tx) {
        return tx.amount !== null && tx.amount !== undefined && tx.amount !== '' && Number.isFinite(Number(tx.amount));
      }).map(function (tx) {
        const outgoing = Boolean((liveUser && tx.senderId === liveUser.id) || tx.fromAccountId === mainAccountId);
        const amount = Number(tx.amount) * (outgoing ? -1 : 1);
        const type = String(tx.type || '').toUpperCase();
        const icon = type === 'INTEREST' ? 'coin' : (type === 'BILL' || type === 'AIRTIME' || type === 'PAYMENT' ? 'bill' : (outgoing ? 'send' : 'up'));
        const dateValue = tx.createdAt || tx.created_at || tx.timestamp;
        const date = dateValue ? new Date(dateValue) : null;
        const dateValid = date && Number.isFinite(date.getTime());
        const dateLabel = dateValid ? date.toLocaleDateString() : 'Date unavailable';
        const status = String(tx.status || 'unknown').toLowerCase();
        const label = outgoing ? 'Paid' : 'Received';
        return [icon, String(tx.description || tx.type || 'Account activity'), label + ' · ' + dateLabel, amount, status, dateValid ? date.getTime() : 0];
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
        if (!session || typeof session.accessToken !== 'string' || !session.accessToken.trim() ||
            typeof session.refreshToken !== 'string' || !session.refreshToken.trim() || !session.user || typeof session.user !== 'object') {
          throw new Error('The server returned an incomplete session.');
        }
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

  // Keep the compact home/activity surfaces honest when the ledger endpoint is down.
  // A failed history request must not look like a genuinely empty account.
  const previousHomeScreen = screens.home;
  screens.home = function () {
    let html = previousHomeScreen();
    if (!demoMode && transactionHistoryUnavailable) {
      const notice = '<div class="sec"><div class="fine" style="padding:12px 0">Recent activity is temporarily unavailable. Your account balance loaded successfully. <button type="button" class="chip" onclick="backendRetryTransactions()">Retry activity</button></div></div>';
      html = html.replace('<div class="sec">', notice + '<div class="sec">');
    }
    return html;
  };
  const previousActivityScreen = screens.activity;
  screens.activity = function () {
    let html = previousActivityScreen();
    if (!demoMode) {
      html = html.replace('<div class="pad fine" style="padding-top:20px">Sample data for this prototype.</div>', '');
      if (transactionHistoryUnavailable) {
        html = html.replace('Nothing here yet.', 'Transaction history is temporarily unavailable. <button type="button" class="chip" onclick="backendRetryTransactions()">Retry</button>');
        html = html.replace('</div><div class="pad fine" style="padding-top:20px">Sample data for this prototype.</div>', '</div><div class="pad" style="padding-top:16px"><button type="button" class="btn sec2" onclick="backendRetryTransactions()">Retry transaction history</button></div>');
      }
    }
    return html;
  };

  const previousBankScreen = screens.bank;
  screens.bank = function () {
    let html = previousBankScreen();
    if (!demoMode) {
      const autoTransferLabel = liveAutoTransferCount === null ? 'Unavailable' : liveAutoTransferCount + ' active';
      html = html.replace('2 active', autoTransferLabel);
      html = html.replace(/<div class="sec"><div class="sech">Recommended for you<\/div>[\s\S]*?(?=<div class="sec"><div class="sech">Save &amp; grow<\/div>)/, '<div class="sec"><div class="sech">Recommended for you</div><div class="fine" style="padding:12px 0">Personalized recommendations are not connected to your account yet.</div></div>');
      html = html.replace(/<div class="sec"><div class="sech">Save &amp; grow<\/div>[\s\S]*?(?=<div class="sec"><div class="sech">Borrow<\/div>)/, '<div class="sec"><div class="sech">Save &amp; grow</div><div class="fine" style="padding:12px 0">Savings products, rates and goals are not connected to your account yet.</div></div>');
      html = html.replace(/<div class="sec"><div class="sech">Insights<\/div>[\s\S]*?(?=<div class="sec"><div style="font-size:14px;font-weight:800">Deposit Protection Fund)/, '<div class="sec"><div class="sech">Insights</div><div class="fine" style="padding:12px 0">Spending insights are not available until real transaction analytics are connected.</div></div>');
      html = html.replace(/<div class="sec"><div class="sech">Borrow<\/div>[\s\S]*?(?=<div class="sec"><div class="sech">Insights<\/div>)/, '<div class="sec"><div class="sech">Borrow</div><div class="fine" style="padding:12px 0">Loan and credit products are not connected to your account yet.</div></div>');
    }
    const rows = liveTransactions.slice(0, 8).map(function (tx) {
      const label = String(tx.description || tx.title || tx.type || tx.reference || 'Account activity').replace(/[<>&"]/g, '');
      const rawAmount = tx.amount;
      const amountValid = rawAmount !== null && rawAmount !== undefined && rawAmount !== '' && Number.isFinite(Number(rawAmount));
      const amount = amountValid ? Number(rawAmount) : null;
      const credit = amount !== null ? amount > 0 : String(tx.direction || '').toUpperCase() === 'CREDIT';
      const date = tx.createdAt || tx.created_at || tx.timestamp;
      const parsedDate = date ? new Date(date) : null;
      const dateLabel = parsedDate && Number.isFinite(parsedDate.getTime())
        ? parsedDate.toLocaleDateString().replace(/[<>&"]/g, '')
        : 'Date unavailable';
      const amountLabel = amount === null ? 'Amount unavailable' : (credit ? '+' : '') + fmt(Math.abs(amount)) + ' RWF';
      return '<div class="row" style="cursor:default"><div class="ico">' + (credit ? '＋' : '−') + '</div>' +
        '<div style="min-width:0;flex:1"><div class="t">' + label + '</div><div class="s">' +
        dateLabel + '</div></div>' +
        '<div class="r" style="color:' + (credit ? 'var(--ok)' : 'var(--tx)') + '">' + amountLabel + '</div></div>';
    }).join('');
    if (demoMode) return previousBankScreen() + '<div class="sec"><div class="sech">Demo account activity</div><div class="fine" style="padding:12px 0">Sample data only. This demo is not connected to a real account or ledger.</div></div>';
    return html + '<div class="sec"><div class="sech">Real account activity</div>' +
      (transactionHistoryUnavailable
        ? '<div class="fine" style="padding:12px 0">Transaction history is temporarily unavailable. Your account balance loaded, but activity could not be refreshed. <button type="button" class="chip" onclick="backendRetryTransactions()">Retry</button></div>'
        : (rows || '<div class="fine" style="padding:12px 0">No account transactions yet.</div>')) +
      '<div class="fine" style="padding-top:8px">Loaded from your itunda account ledger.</div></div>';
  };
  window.backendRetryTransactions = async function () {
    if (demoMode) return;
    try {
      const response = await api('/api/v1/account/transactions');
      if (!response || !Array.isArray(response.transactions)) {
        throw new Error('Transaction history returned an invalid response.');
      }
      liveTransactions = response.transactions;
      transactionHistoryUnavailable = false;
      if (Array.isArray(S.act)) {
        S.act = liveTransactions.filter(function (tx) {
          return tx.amount !== null && tx.amount !== undefined && tx.amount !== '' && Number.isFinite(Number(tx.amount));
        }).map(function (tx) {
          const outgoing = Boolean((liveUser && tx.senderId === liveUser.id) || tx.fromAccountId === mainAccountId);
          const amount = Number(tx.amount) * (outgoing ? -1 : 1);
          const type = String(tx.type || '').toUpperCase();
          const icon = type === 'INTEREST' ? 'coin' : (type === 'BILL' || type === 'AIRTIME' || type === 'PAYMENT' ? 'bill' : (outgoing ? 'send' : 'up'));
          const dateValue = tx.createdAt || tx.created_at || tx.timestamp;
          const date = dateValue ? new Date(dateValue) : null;
          const dateValid = date && Number.isFinite(date.getTime());
          const dateLabel = dateValid ? date.toLocaleDateString() : 'Date unavailable';
          const status = String(tx.status || 'unknown').toLowerCase();
          return [icon, String(tx.description || tx.type || 'Account activity'), (outgoing ? 'Paid' : 'Received') + ' · ' + dateLabel, amount, status, dateValid ? date.getTime() : 0];
        });
      }
      render('fade-none');
    } catch (error) {
      transactionHistoryUnavailable = true;
      fail(new Error('Transaction history is still unavailable. Your balance is unchanged; please retry.'));
      render('fade-none');
    }
  };

  const previousSentScreen = screens.sent;
  screens.sent = function () {
    let html = previousSentScreen();
    const reference = lastTransaction && lastTransaction.referenceNumber ? String(lastTransaction.referenceNumber) : '—';
    return html.replace('IT-20481-7', reference.replace(/[<>&"]/g, ''));
  };
  // Profile-name editing is prototype-only: the canonical backend currently exposes
  // profile reads and specific profile subresources, but no supported name-update API.
  // Never report a local-only edit as persisted for a signed-in account.
  const prototypeSaveProfileName = window.pSave;
  window.pSave = function () {
    if (demoMode && typeof prototypeSaveProfileName === 'function') {
      return prototypeSaveProfileName.apply(this, arguments);
    }
    return fail(new Error('Changing your profile name is not connected to a supported account service yet. No changes were saved.'));
  };

  function formatProfileDate(value) {
    if (value === null || value === undefined || value === '') return 'Unavailable';
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date.toLocaleDateString() : 'Unavailable';
  }

  const previousProfileScreen = screens.profile;
  screens.profile = function () {
    let html = previousProfileScreen();
    if (demoMode) return html;
    if (liveUser) {
      const fullName = [liveUser.firstName, liveUser.lastName].filter(Boolean).join(' ').replace(/[<>&"]/g, '');
      html = html.replace('078 ••• 214', String(liveUser.phoneNumber || 'Phone unavailable').replace(/[<>&"]/g, ''));
      html = html.replace('Your details are sample data.', 'Your details are loaded from your itunda account.');
      html = html.replace('March 2025', formatProfileDate(liveUser.createdAt));
      html = html.replace('2,000,000 RWF', 'Unavailable');
      const verificationStatus = liveUser.kycVerified === true ? 'Verified' : liveUser.kycVerified === false ? 'Not verified' : 'Unavailable';
      const verificationLabel = verificationStatus === 'Verified' ? 'Identity verified' : verificationStatus === 'Not verified' ? 'Identity not verified' : 'Identity status unavailable';
      html = html.replace('Identity verified', verificationLabel);
      html = html.replace('Amina Uwase', fullName || 'Name unavailable');
    } else {
      html = html.replace('Amina Uwase', 'Name unavailable');
      html = html.replace('078 ••• 214', 'Phone unavailable');
      html = html.replace('March 2025', 'Unavailable');
      html = html.replace('2,000,000 RWF', 'Unavailable');
      html = html.replace('Verified', 'Unavailable');
      html = html.replace('Your details are sample data.', 'Your account profile could not be loaded. Please retry.');
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
      if (!session || typeof session.accessToken !== 'string' || !session.accessToken.trim() ||
            typeof session.refreshToken !== 'string' || !session.refreshToken.trim() || !session.user || typeof session.user !== 'object') {
          throw new Error('The server returned an incomplete session.');
        }
      localStorage.setItem(ACCESS_KEY, session.accessToken);
      localStorage.setItem(REFRESH_KEY, session.refreshToken);
      localStorage.setItem('itunda_web_user', JSON.stringify(session.user));
      liveUser = session.user;
      try {
        await loadAccount();
      } catch (error) {
        // Registration succeeded and the session is real. Do not report account
        // creation failure or invite a duplicate registration when only data loading failed.
        S.ob.pin = '';
        S.obStep = 'backendLogin';
        render('fade-none');
        showAccountLoadRetry('Your account was created and you are signed in, but account data could not be loaded. Retry without creating another account or entering your PIN again.');
        console.warn('[itunda backend] Account created; initial account load failed:', error.message);
        return;
      }
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
      const intentFingerprint = JSON.stringify({ recipient: recipient, amount: amount, fromAccountId: mainAccountId });
      if (!transferIdempotencyKey || transferIntentFingerprint !== intentFingerprint) {
        transferIdempotencyKey = window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() :
          'web-' + Date.now() + '-' + Math.random().toString(36).slice(2);
        transferIntentFingerprint = intentFingerprint;
      }
      const result = await api('/api/v1/p2p/send', {
        method: 'POST',
        headers: { 'Idempotency-Key': transferIdempotencyKey },
        body: JSON.stringify({ recipient: recipient, amount: amount, description: 'Transfer from itunda web app', fromAccountId: mainAccountId })
      });
      // The API contract returns success + the ledger transaction. A malformed 2xx
      // response must not be rendered as a confirmed receipt; retain the idempotency
      // key so the same intent can be safely retried/replayed.
      if (!result || result.success !== true || !result.transaction || typeof result.transaction !== 'object' || Array.isArray(result.transaction) || !String(result.transaction.id || result.transaction.referenceNumber || '').trim() || result.transaction.amount === null || result.transaction.amount === undefined || result.transaction.amount === '' || !Number.isFinite(Number(result.transaction.amount)) || Number(result.transaction.amount) !== amount) {
        throw new Error('Transfer may have been submitted, but confirmation details are unavailable. Check your activity before retrying.');
      }
      transferIdempotencyKey = null;
      transferIntentFingerprint = null;
      S.sentAmt = amount;
      lastTransaction = result.transaction;
      const returnedBalance = Number(result.newBalance);
      if (result.newBalance !== null && result.newBalance !== undefined && result.newBalance !== '' && Number.isFinite(returnedBalance)) {
        S.bank = returnedBalance;
      }
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
