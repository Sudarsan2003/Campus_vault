import Logo from '../ui/Logo.jsx';
import LedgerPanel from './LedgerPanel.jsx';

export default function AuthShell({ title, subtitle, children, footer }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[5fr_6fr]">
      {/* Ledger side */}
      <aside className="hidden flex-col justify-between bg-ink-900 px-10 py-10 text-white lg:flex xl:px-14">
        <Logo dark />

        <div className="max-w-md">
          <h2 className="text-[32px] font-semibold leading-[1.15] tracking-tight">A shared record that nobody can quietly edit.</h2>
          <p className="mt-4 text-[15px] leading-relaxed text-ink-300">
            Borrowing, returns and ownership changes are written to the blockchain, so anyone on campus can check what actually happened to an asset.
          </p>
          <div className="mt-10">
            <LedgerPanel />
          </div>
        </div>

        <p className="text-[13px] text-ink-400">Sample history shown for illustration.</p>
      </aside>

      {/* Form side */}
      <main className="flex flex-col px-5 py-8 sm:px-10">
        <Logo className="mb-10 lg:hidden" />
        <div className="mx-auto flex w-full max-w-[26rem] flex-1 flex-col justify-center py-4">
          <h1 className="text-[28px] font-semibold tracking-tight">{title}</h1>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-600">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <div className="mt-8 text-[15px] text-ink-600">{footer}</div>
        </div>
      </main>
    </div>
  );
}