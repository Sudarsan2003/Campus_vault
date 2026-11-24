import ChainBadge from '../ui/ChainBadge.jsx';

const short = (h) => `${h.slice(0, 8)}…${h.slice(-6)}`;

export default function LedgerTab({ entries }) {
  if (entries.length === 0) {
    return (
      <p className="rounded-xl border border-paper-300 bg-white px-4 py-8 text-center text-[15px] text-ink-600">
        Nothing on the ledger yet. Register an asset to write the first block.
      </p>
    );
  }

  return (
    <ol className="space-y-3" aria-label="Blockchain ledger">
      {entries.map((e) => (
        <li key={e.id} className="rounded-xl border border-paper-300 bg-white px-4 py-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-[15px] font-medium">{e.title}</p>
              <p className="truncate text-[13px] text-ink-600">{e.detail}</p>
            </div>
            <ChainBadge status={e.chainStatus} detail={e.chainDetail}
              labels={{ VERIFIED: 'Confirmed on chain', MISMATCH: 'Not on chain' }} />
          </div>
          {e.chainStatus === 'MISMATCH' && <p className="mt-1 text-[13px] text-alert">{e.chainDetail}</p>}
          <p className="mt-2 font-mono text-xs text-ink-400">
            {short(e.txHash)} · block {e.blockNumber.toLocaleString()} · {new Date(e.createdAt).toLocaleString()}
          </p>
        </li>
      ))}
    </ol>
  );
}