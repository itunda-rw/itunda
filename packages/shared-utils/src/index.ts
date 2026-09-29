// itunda's own established convention (see docs/DESIGN_REFERENCES.md's money-
// formatting sweep): "10,346 RWF", not "RWF 10,346" or a currency-symbol prefix.
// Uses a FIXED locale rather than the viewer's own browser/OS locale -- an
// unqualified `Intl`/`toLocaleString()` call picks up whatever locale the
// browser reports (e.g. a Rwandan user on a French or Kinyarwanda locale would
// see "10 346" or "10.346"), which is a real ambiguity risk for money amounts
// specifically, not just a cosmetic inconsistency.
export const formatMoney = (amount: number, currency: string = 'RWF'): string => {
  return `${amount.toLocaleString('en-US')} ${currency}`;
};
