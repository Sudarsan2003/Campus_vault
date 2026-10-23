import { assetService } from '../../services/assetService.js';

export default function LoansTab({ loans, user, run, busy }) {
  if (loans.length === 0) {
    return (
      <p className="rounded-xl border border-paper-300 bg-white px-4 py-8 text-center text-[15px] text-ink-600">
        No loans yet. Borrow an available asset, or lend one of yours.
      </p>
    );
  }

  return (
    <ul className="divide-y divide-paper-200 rounded-xl border border-paper-300 bg-white">
      {loans.map((l) => {
        const open = l.status === 'OPEN';
        const canVerify = open && (l.ownerId === user.id || user.role === 'ADMIN');
        return (
          <li key={l.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5">
            <div className="min-w-0">
              <p className="text-[15px] font-medium">
                {l.assetName} <span className="ml-1 font-mono text-xs text-ink-400">{l.assetCode}</span>
              </p>
              <p className="text-[13px] text-ink-600">
                {l.borrowerId === user.id ? 'Borrowed by you' : `Borrowed by ${l.borrowerName}`} · due{' '}
                {new Date(l.dueAt).toLocaleDateString()} · deposit {l.deposit.toLocaleString()} tokens
              </p>
            </div>
            <div className="flex items-center gap-2">
              {open ? (
                <span className="rounded-full bg-pending/15 px-2 py-0.5 text-xs font-medium text-ink-800">On loan</span>
              ) : (
                <span className="rounded-full bg-verified/15 px-2 py-0.5 text-xs font-medium text-verified">
                  {l.damaged ? 'Returned damaged' : 'Returned'}
                </span>
              )}
              {canVerify && (
                <>
                  <button className="btn-quiet" disabled={busy} onClick={() => run(() => assetService.returnLoan(l.id, false))}>
                    Verify return
                  </button>
                  <button className="btn-quiet" disabled={busy} onClick={() => run(() => assetService.returnLoan(l.id, true))}>
                    Returned damaged
                  </button>
                </>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}