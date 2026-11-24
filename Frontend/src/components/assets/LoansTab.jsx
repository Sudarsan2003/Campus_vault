import { assetService } from '../../services/assetService.js';
import ChainBadge, { ChainProblem } from '../ui/ChainBadge.jsx';

const fmt = (n) => n.toLocaleString();
const days = (n) => `${n} day${n === 1 ? '' : 's'}`;

function Badge({ loan }) {
  if (loan.status === 'OPEN') {
    return loan.overdue ? (
      <span className="rounded-full bg-alert-soft px-2 py-0.5 text-xs font-medium text-alert">
        Overdue · {days(loan.daysOverdue)}
      </span>
    ) : (
      <span className="rounded-full bg-pending/15 px-2 py-0.5 text-xs font-medium text-ink-800">On loan</span>
    );
  }
  const label = loan.damaged ? 'Returned damaged' : loan.lateFee > 0 ? 'Returned late' : 'Returned';
  const cls = loan.damaged || loan.lateFee > 0 ? 'bg-pending/15 text-ink-800' : 'bg-verified/15 text-verified';
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>{label}</span>;
}

function LoanRow({ loan, user, run, busy }) {
  const canVerify = loan.status === 'OPEN' && loan.chainStatus !== 'MISMATCH' && (loan.ownerId === user.id || user.role === 'ADMIN');
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5">
      <div className="min-w-0">
        <p className="text-[15px] font-medium">
          {loan.assetName} <span className="ml-1 font-mono text-xs text-ink-400">{loan.assetCode}</span>
        </p>
        <p className="text-[13px] text-ink-600">
          {loan.borrowerId === user.id ? 'Borrowed by you' : `Borrowed by ${loan.borrowerName}`} · due{' '}
          {new Date(loan.dueAt).toLocaleDateString()} · deposit {fmt(loan.deposit)} tokens
        </p>
        <ChainProblem status={loan.chainStatus} detail={loan.chainDetail} />
        {loan.status === 'OPEN' && loan.overdue && (
          <p className="mt-0.5 text-[13px] text-alert">
            Late fee so far: {fmt(loan.lateFee)} of {fmt(loan.deposit)} tokens, going to the owner on return.
          </p>
        )}
        {loan.status !== 'OPEN' && loan.lateFee > 0 && (
          <p className="mt-0.5 text-[13px] text-ink-600">
            Late fee {fmt(loan.lateFee)} tokens kept for the owner · {fmt(loan.deposit - loan.lateFee)} refunded
          </p>
        )}
      </div>
      <div className="flex items-center gap-2">
        <ChainBadge status={loan.chainStatus} detail={loan.chainDetail} />
        <Badge loan={loan} />
        {canVerify && (
          <>
            <button className="btn-quiet" disabled={busy} onClick={() => run(() => assetService.returnLoan(loan.id, false))}>
              Verify return
            </button>
            <button className="btn-quiet" disabled={busy} onClick={() => run(() => assetService.returnLoan(loan.id, true))}>
              Returned damaged
            </button>
          </>
        )}
      </div>
    </li>
  );
}

export default function LoansTab({ loans, overdue = [], user, run, busy }) {
  const isAdmin = user.role === 'ADMIN';
  return (
    <div className="space-y-6">
      {isAdmin && (
        <section>
          <p className="mb-2 text-[15px] font-medium">Overdue across campus</p>
          {overdue.length === 0 ? (
            <p className="rounded-xl border border-paper-300 bg-white px-4 py-5 text-center text-[14px] text-ink-600">
              No overdue loans.
            </p>
          ) : (
            <ul className="divide-y divide-paper-200 rounded-xl border border-alert/30 bg-white">
              {overdue.map((l) => <LoanRow key={l.id} loan={l} user={user} run={run} busy={busy} />)}
            </ul>
          )}
        </section>
      )}

      <section>
        {isAdmin && <p className="mb-2 text-[15px] font-medium">Your loans</p>}
        {loans.length === 0 ? (
          <p className="rounded-xl border border-paper-300 bg-white px-4 py-8 text-center text-[15px] text-ink-600">
            No loans yet. Borrow an available asset, or lend one of yours.
          </p>
        ) : (
          <ul className="divide-y divide-paper-200 rounded-xl border border-paper-300 bg-white">
            {loans.map((l) => <LoanRow key={l.id} loan={l} user={user} run={run} busy={busy} />)}
          </ul>
        )}
        <p className="mt-3 text-[13px] text-ink-600">
          Returning late keeps 10% of the deposit for each day past the due date, up to the whole deposit. The
          smart contract works this out when the return is verified.
        </p>
      </section>
    </div>
  );
}
