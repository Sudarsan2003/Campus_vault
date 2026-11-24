import { useState } from 'react';
import { Ban, CheckCircle2, Loader2, ShieldAlert, ShieldCheck, Swords } from 'lucide-react';
import ChainBadge from '../ui/ChainBadge.jsx';
import Alert from '../ui/Alert.jsx';
import { assetService } from '../../services/assetService.js';
import { parseApiError } from '../../lib/api.js';

const FIELDS = [
  ['loanOpen', 'Loan open on-chain', (v) => (v ? 'Yes' : 'No')],
  ['assetStatus', 'Asset status', (v) => v],
  ['borrowerTokens', 'Borrower tokens', (v) => v.toLocaleString()],
  ['ownerTokens', 'Owner tokens', (v) => v.toLocaleString()],
];

const short = (a) => (a ? `${a.slice(0, 6)}…${a.slice(-4)}` : '');

// What CampusVault shows afterwards: the database still has the loan open, the verifier compares it with the chain.
function DetectionPanel({ d }) {
  const bad = d.status === 'MISMATCH';
  const SettleIcon = d.settleRefused ? Ban : CheckCircle2;
  return (
    <div className={`mt-4 rounded-lg border p-3 ${bad ? 'border-alert/40 bg-alert-soft/40' : 'border-paper-300 bg-paper'}`}>
      <p className="text-[12px] font-medium uppercase tracking-wide text-ink-400">Detection in CampusVault</p>
      <table className="mt-1.5 w-full text-[14px]">
        <tbody>
          <tr>
            <td className="py-1 text-ink-600">Database (My loans)</td>
            <td className="py-1">{d.databaseSays}</td>
          </tr>
          <tr className="border-t border-paper-200">
            <td className="py-1 text-ink-600">Blockchain</td>
            <td className={`py-1 ${bad ? 'font-semibold text-alert' : ''}`}>{d.chainSays}</td>
          </tr>
          <tr className="border-t border-paper-200">
            <td className="py-1 text-ink-600">Chain verifier</td>
            <td className="py-1"><ChainBadge status={d.status} detail={d.detail} /></td>
          </tr>
        </tbody>
      </table>
      {bad && <p className="mt-1.5 text-[13px] text-alert">{d.detail}</p>}
      <p className={`mt-2 flex items-start gap-1.5 text-[13px] ${d.settleRefused ? 'text-alert' : 'text-verified'}`}>
        <SettleIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
        <span title={d.settleMessage}>
          {d.settleRefused
            ? 'Verify return refused: the owner can’t settle this loan until an administrator investigates.'
            : d.settleMessage}
        </span>
      </p>
    </div>
  );
}

function RunCard({ run }) {
  const ok = run.attackSucceeded;
  const Icon = ok ? ShieldAlert : ShieldCheck;
  return (
    <section className={`rounded-xl border bg-white p-4 ${ok ? 'border-alert/40' : 'border-verified/40'}`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-mono text-[15px] font-semibold">{run.contract}</p>
          <p className="mt-0.5 text-[13px] text-ink-600">{run.description}</p>
        </div>
        <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${ok ? 'bg-alert-soft text-alert' : 'bg-verified/15 text-verified'}`}>
          <Icon className="h-3.5 w-3.5" aria-hidden /> {ok ? 'Attack succeeded' : 'Attack blocked'}
        </span>
      </div>

      <table className="mt-4 w-full text-[14px]">
        <thead>
          <tr className="text-left text-[12px] uppercase tracking-wide text-ink-400">
            <th className="pb-1.5 font-medium">Field</th>
            <th className="pb-1.5 font-medium">Before attack</th>
            <th className="pb-1.5 font-medium">After attack</th>
          </tr>
        </thead>
        <tbody>
          {FIELDS.map(([key, label, fmt]) => {
            const changed = run.before[key] !== run.after[key];
            return (
              <tr key={key} className="border-t border-paper-200">
                <td className="py-2 text-ink-600">{label}</td>
                <td className="py-2">{fmt(run.before[key])}</td>
                <td className={`py-2 ${changed ? 'font-semibold text-alert' : ''}`}>{fmt(run.after[key])}</td>
              </tr>
            );
          })}
        </tbody>
      </table>

      <p className={`mt-3 text-[13px] ${ok ? 'text-alert' : 'text-verified'}`}>{run.outcome}</p>
      <p className="mt-1 font-mono text-xs text-ink-400">
        contract {short(run.contractAddress)} · operator {short(run.operator)} · attacker {short(run.attacker)}
        {run.txHash && ` · tx ${short(run.txHash)} · block ${run.blockNumber} · gas ${run.gasUsed.toLocaleString()}`}
      </p>
      {run.detection && <DetectionPanel d={run.detection} />}
    </section>
  );
}

export default function SecurityLab() {
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const runAttack = async () => {
    setBusy(true);
    setError('');
    try {
      setResult(await assetService.securityDemo());
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-5">
      <div className="rounded-xl border border-paper-300 bg-white p-4">
        <p className="text-[15px] font-medium">Injected attack: improper access control</p>
        <p className="mt-1 text-[14px] text-ink-600">
          In CampusVault only the operator (the back end) may change the contract, enforced by the{' '}
          <code className="font-mono text-[13px]">onlyOperator</code> modifier. Here a copy of the contract has that guard
          removed from <code className="font-mono text-[13px]">returnAsset()</code>. A loan is set up on each contract, then a
          different wallet calls <code className="font-mono text-[13px]">returnAsset(loanId, false)</code> directly, closing a
          loan whose item was never returned.
        </p>
        <p className="mt-2 text-[13px] text-ink-400">
          Each run deploys fresh throwaway copies of both contracts on the local chain with two Anvil test accounts. Your real
          contracts and data are not touched.
        </p>
        <button type="button" className="btn-primary mt-4 sm:w-auto" disabled={busy} onClick={runAttack}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Swords className="h-4 w-4" aria-hidden />}
          {busy ? 'Running attack…' : result ? 'Run attack again' : 'Run attack'}
        </button>
      </div>

      {error && <Alert>{error}</Alert>}

      {result && (
        <>
          <div className="grid gap-4 lg:grid-cols-2">
            {result.runs.map((r) => <RunCard key={r.contract} run={r} />)}
          </div>
          <div className="rounded-xl border border-paper-300 bg-white p-4 text-[14px] text-ink-600">
            <p className="font-medium text-ink-900">What this shows</p>
            <ul className="mt-1.5 list-disc space-y-1 pl-5">
              <li>Vulnerable copy: one transaction closes the loan, refunds the deposit and lists the item as available, while the borrower still has it.</li>
              <li>Real contract: the same call reverts with “Only the operator”, and nothing changes.</li>
              <li>Detection: the attack skipped the app, so CampusVault’s database still says the loan is open. The chain verifier (the same check My loans uses) sees it closed on-chain, flags it “Doesn’t match chain” and refuses to verify the return.</li>
            </ul>
          </div>
        </>
      )}
    </div>
  );
}
