import { Check, Loader2 } from 'lucide-react';

// Illustrative sample only: not read from the chain.
const BLOCKS = [
  { event: 'Asset registered', detail: 'CV-1024 · HP Laptop', hash: '0x72a4…91f0', block: '4,182,091' },
  { event: 'Ownership verified', detail: 'Verified by admin', hash: '0x1be9…c407', block: '4,182,093' },
  { event: 'Borrow approved', detail: 'Deposit locked · 1,000 test tokens', hash: '0x9d30…2a6e', block: '4,182,140' },
  { event: 'Return verified', detail: 'Deposit released', hash: '0x5c81…f3d2', block: '4,182,377', pending: true },
];

const STEP = 0.75; // seconds between blocks

export default function LedgerPanel() {
  return (
    <ol className="relative" aria-label="Sample blockchain history for one asset">
      {BLOCKS.map((b, i) => {
        const last = i === BLOCKS.length - 1;
        return (
          <li
            key={b.hash}
            className="relative flex gap-4 pb-5 opacity-0 last:pb-0 motion-safe:animate-[block-in_0.5s_ease-out_forwards] motion-reduce:opacity-100"
            style={{ animationDelay: `${0.3 + i * STEP}s` }}
          >
            {/* rail */}
            <div className="relative flex w-3 flex-col items-center">
              <span className={`mt-1.5 h-3 w-3 rounded-full border-2 ${last ? 'border-[#5B78FF] bg-ink-900' : 'border-[#5B78FF] bg-[#5B78FF]'}`} />
              {!last && (
                <span
                  className="mt-1 w-px flex-1 origin-top bg-ink-600 motion-safe:animate-[link-grow_0.4s_ease-out_both]"
                  style={{ animationDelay: `${0.6 + i * STEP}s` }}
                />
              )}
            </div>

            <div className="min-w-0 flex-1 rounded-xl border border-white/10 bg-white/[0.04] px-4 py-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-[15px] font-medium text-white">{b.event}</p>
                  <p className="truncate text-[13px] text-ink-300">{b.detail}</p>
                </div>
                <StatusChip pending={b.pending} delay={0.3 + i * STEP} />
              </div>
              <p className="mt-2 font-mono text-xs text-ink-400">
                {b.hash} <span className="text-ink-600">·</span> block {b.block}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function StatusChip({ pending, delay }) {
  if (!pending) {
    return (
      <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-verified/20 px-2 py-0.5 text-xs font-medium text-[#5FD7B0]">
        <Check className="h-3 w-3" aria-hidden /> Confirmed
      </span>
    );
  }
  // Newest block starts as "Pending" and flips to "Confirmed" once, after the chain has drawn.
  const flipAt = delay + 1.2;
  return (
    <span className="relative inline-flex h-[22px] w-[92px] shrink-0 justify-end">
      <span
        className="absolute inset-0 flex items-center justify-end gap-1 motion-safe:animate-[confirm-flip_1s_linear_both] motion-reduce:hidden"
        style={{ animationDelay: `${flipAt - 0.6}s`, animationDuration: '1s' }}
      >
        <span className="inline-flex items-center gap-1 rounded-full bg-pending/25 px-2 py-0.5 text-xs font-medium text-[#F0C46B]">
          <Loader2 className="h-3 w-3 animate-spin" aria-hidden /> Pending
        </span>
      </span>
      <span
        className="absolute inset-0 flex items-center justify-end opacity-0 motion-safe:animate-[confirm-flip-in_1s_linear_forwards] motion-reduce:opacity-100"
        style={{ animationDelay: `${flipAt - 0.6}s`, animationDuration: '1s' }}
      >
        <span className="inline-flex items-center gap-1 rounded-full bg-verified/20 px-2 py-0.5 text-xs font-medium text-[#5FD7B0]">
          <Check className="h-3 w-3" aria-hidden /> Confirmed
        </span>
      </span>
    </span>
  );
}