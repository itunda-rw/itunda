// Real per-bucket ledger (2026-08-31, direct user-supplied Toss Bank screenshots:
// 보관하기/매일모으기 each get their own full-screen ledger, not just an inline card) --
// mirrors backend's own BucketTransactionDto exactly (services/backend/savings/.../
// BucketTransactionDto.kt). One normalized shape every savings bucket's own transaction
// endpoint returns, so BucketDetailScreen.tsx can render any of them identically.
export interface BucketTransaction {
  id: string;
  description: string;
  amount: number;
  isCredit: boolean;
  balanceAfter: number;
  createdAt: string;
}

// The Youth account is a real Account (unlike every other bucket above), so its own
// history already comes back as generic Transaction[] from the existing
// /api/v1/account/{id}/transactions endpoint, not a pre-normalized BucketTransactionDto
// -- this adapts it into the same shape client-side, same running-balance approach
// AccountDetailScreen.tsx's own withBalance already established (newest-first, working
// backward from the account's current real balance).
export function transactionsToBucketTransactions(
  transactions: { id: string; description: string; amount: number; fromAccountId: string | null; toAccountId: string | null; createdAt: string }[],
  accountId: string,
  currentBalance: number,
): BucketTransaction[] {
  const sorted = [...transactions].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  let runningBalance = currentBalance;
  return sorted.map((tx) => {
    const isCredit = tx.toAccountId === accountId && tx.fromAccountId !== accountId;
    const balanceAfter = runningBalance;
    runningBalance -= isCredit ? tx.amount : -tx.amount;
    return { id: tx.id, description: tx.description, amount: tx.amount, isCredit, balanceAfter, createdAt: tx.createdAt };
  });
}
