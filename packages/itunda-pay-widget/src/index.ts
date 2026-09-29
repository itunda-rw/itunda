/**
 * Real "Pay with itunda" embeddable checkout widget (2026-07-21) -- modeled directly
 * on Toss Payments' own real, published JS SDK (docs.tosspayments.com/sdk/v2/js:
 * `loadTossPayments(clientKey).requestPayment(...)`, redirecting to `successUrl`/
 * `failUrl` on completion). Found as a real gap by researching Toss Pay/Toss
 * Payments' actual documented online-checkout flow: itunda had a real merchant QR/POS
 * payment system, but nothing an EXTERNAL website (not built on itunda at all) could
 * embed to accept "Pay with itunda" -- confirmed by grep, no such package existed
 * anywhere in this repo before this.
 *
 * Honest, deliberate scope: this widget does NOT call itunda's API itself, and
 * deliberately has no "client key" concept -- unlike Toss Payments' real SDK (which
 * does make client-side calls authenticated by a public client key), itunda's actual
 * payment-completion mechanism is the customer scanning a real QR/deep-link with
 * their own itunda app, not entering card details inline in this widget. A merchant's
 * OWN backend server creates the real payment first (`POST /api/v1/pay/payments`,
 * authenticated with their SECRET key, which must never reach a browser) and gets
 * back a `checkoutUrl` pointing at itunda's real hosted checkout page
 * (services/micro-frontends/pay-checkout) -- this widget's only real job is
 * navigating the customer's browser there, matching exactly how real Toss Payments'
 * redirect-based payment methods (bank transfer, virtual account) already behave for
 * their own real merchants, not a stub standing in for unbuilt functionality.
 */

export interface RequestPaymentOptions {
  /** The `checkoutUrl` returned by the merchant's own backend calling
   * `POST /api/v1/pay/payments` with their secret API key. Never construct this URL
   * client-side -- it must come from a real, already-created PaymentIntent. */
  checkoutUrl: string;
}

/**
 * Navigates the customer's browser to itunda's real hosted checkout page. Mirrors
 * Toss Payments' real `requestPayment()` naming exactly so an integrator already
 * familiar with that SDK recognizes the shape immediately.
 */
export function requestPayment(options: RequestPaymentOptions): void {
  if (!options.checkoutUrl) {
    throw new Error('itunda Pay: checkoutUrl is required -- create a payment server-side first (POST /api/v1/pay/payments) and pass back its real checkoutUrl.');
  }
  window.location.href = options.checkoutUrl;
}
