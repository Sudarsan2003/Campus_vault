import { AlertTriangle, Check, HelpCircle } from 'lucide-react';

// Shows the result of the backend re-checking a record against the blockchain.
const LOOK = {
  VERIFIED: { Icon: Check, cls: 'bg-verified/15 text-verified' },
  MISMATCH: { Icon: AlertTriangle, cls: 'bg-alert-soft text-alert' },
  UNAVAILABLE: { Icon: HelpCircle, cls: 'bg-paper-200 text-ink-600' },
};

export default function ChainBadge({ status, detail, labels }) {
  if (!status) return null;
  const { Icon, cls } = LOOK[status] ?? LOOK.UNAVAILABLE;
  const text = {
    VERIFIED: 'On-chain',
    MISMATCH: "Doesn't match chain",
    UNAVAILABLE: "Can't check chain",
    ...labels,
  }[status];
  return (
    <span title={detail} className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>
      <Icon className="h-3 w-3" aria-hidden /> {text}
    </span>
  );
}

/** The reason, shown under a record that failed the check. */
export function ChainProblem({ status, detail }) {
  if (status !== 'MISMATCH') return null;
  return <p className="mt-0.5 text-[13px] text-alert">{detail}</p>;
}
