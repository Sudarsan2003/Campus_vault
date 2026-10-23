import { useCallback, useEffect, useState } from 'react';
import { Coins, LogOut } from 'lucide-react';
import Logo from '../components/ui/Logo.jsx';
import Alert from '../components/ui/Alert.jsx';
import AssetsTab from '../components/assets/AssetsTab.jsx';
import LoansTab from '../components/assets/LoansTab.jsx';
import LedgerTab from '../components/assets/LedgerTab.jsx';
import ReportsTab from '../components/reports/ReportsTab.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { assetService } from '../services/assetService.js';
import { reportService } from '../services/reportService.js';
import { parseApiError } from '../lib/api.js';

const TABS = ['Assets', 'Lost & found', 'My loans', 'Ledger'];

export default function Dashboard() {
  const { user, logout } = useAuth();
  const [tab, setTab] = useState('Assets');
  const [data, setData] = useState({ assets: [], loans: [], ledger: [], reports: [], wallet: null });
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const [assets, loans, ledger, reports, wallet] = await Promise.all([
        assetService.list(),
        assetService.loans(),
        assetService.ledger(),
        reportService.list(),
        assetService.wallet().catch(() => null), // the wallet needs the chain; the rest doesn't
      ]);
      setData({ assets, loans, ledger, reports, wallet });
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setLoaded(true);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Runs a write action, shows any error, then reloads everything. Returns true on success.
  const run = useCallback(
    async (action) => {
      setBusy(true);
      setError('');
      try {
        await action();
        await refresh();
        return true;
      } catch (err) {
        setError(parseApiError(err).message);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [refresh]
  );

  const { wallet } = data;

  return (
    <div className="min-h-screen">
      <header className="border-b border-paper-300 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-5 py-3.5">
          <Logo />
          <button onClick={logout} className="btn-quiet">
            <LogOut className="h-4 w-4" aria-hidden /> Sign out
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-5 py-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-[28px] font-semibold tracking-tight">Welcome, {user.fullName.split(' ')[0]}</h1>
            <p className="mt-1 text-[15px] text-ink-600">
              {user.department} · {user.role === 'ADMIN' ? 'Administrator' : 'Student'}
            </p>
          </div>
          {wallet && (
            <div className="flex items-center gap-3 rounded-xl border border-paper-300 bg-white px-4 py-2.5">
              <Coins className="h-4 w-4 text-chain" aria-hidden />
              <span className="text-[15px]">
                <strong className="font-semibold">{wallet.balance.toLocaleString()}</strong> test tokens
              </span>
              <button className="btn-quiet" disabled={busy} onClick={() => run(assetService.faucet)}>
                Get test tokens
              </button>
            </div>
          )}
        </div>

        <div className="mt-6 space-y-3">
          {error && <Alert>{error}</Alert>}
          {loaded && !wallet && <Alert>Can't reach the blockchain node. Start it with `anvil --state anvil-state.json`, then reload.</Alert>}
        </div>

        <div className="mt-6 flex gap-6 overflow-x-auto border-b border-paper-300" role="tablist">
          {TABS.map((t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`-mb-px shrink-0 border-b-2 pb-2.5 text-[15px] font-medium transition-colors ${
                tab === t ? 'border-chain text-ink-900' : 'border-transparent text-ink-600 hover:text-ink-900'
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        <div className="mt-6">
          {tab === 'Assets' && <AssetsTab assets={data.assets} user={user} run={run} busy={busy} />}
          {tab === 'Lost & found' && <ReportsTab reports={data.reports} user={user} run={run} busy={busy} />}
          {tab === 'My loans' && <LoansTab loans={data.loans} user={user} run={run} busy={busy} />}
          {tab === 'Ledger' && <LedgerTab entries={data.ledger} />}
        </div>
      </main>
    </div>
  );
}