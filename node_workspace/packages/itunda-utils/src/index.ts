export const formatCurrency = (amount: number, currency: string = 'RWF'): string => {
  return new Intl.NumberFormat('en-RW', {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 0
  }).format(amount);
};
