/* Itunda consumer web API bridge. */
(function () {
  'use strict';
  const API_BASE = 'https://api.itunda.im';
  window.itundaApiRequest = async function (path, options) {
    const response = await fetch(API_BASE + path, options || {});
    const body = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(body.message || 'Itunda API request failed.');
    return body;
  };
})();
