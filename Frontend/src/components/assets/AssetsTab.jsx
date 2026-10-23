import { useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import { assetService } from '../../services/assetService.js';

const CATEGORIES = ['Book', 'Laptop', 'Charger', 'Calculator', 'Lab equipment', 'Project material', 'Other'];
const BORROW_DAYS = 7;

const STATUS = {
  PENDING: { label: 'Awaiting verification', cls: 'bg-pending/15 text-ink-800' },
  AVAILABLE: { label: 'Available', cls: 'bg-verified/15 text-verified' },
  BORROWED: { label: 'Borrowed', cls: 'bg-paper-200 text-ink-600' },
};

const empty = { name: '', category: CATEGORIES[0], description: '' };

export default function AssetsTab({ assets, user, run, busy }) {
  const [form, setForm] = useState(empty);
  const set = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) return;
    const ok = await run(() => assetService.create({ ...form, name: form.name.trim() }));
    if (ok) setForm(empty);
  };

  return (
    <div>
      <form onSubmit={submit} className="rounded-xl border border-paper-300 bg-white p-4">
        <p className="text-[15px] font-medium">Register an asset</p>
        <p className="mt-0.5 text-[13px] text-ink-600">
          Its details are hashed and written to the blockchain. An admin then verifies ownership.
        </p>
        <div className="mt-4 grid gap-4 sm:grid-cols-[2fr_1fr]">
          <div>
            <label htmlFor="name" className="mb-1.5 block text-sm font-medium">Name</label>
            <input id="name" name="name" className="field-input" placeholder="HP Pavilion laptop" value={form.name} onChange={set} maxLength={120} />
          </div>
          <div>
            <label htmlFor="category" className="mb-1.5 block text-sm font-medium">Category</label>
            <select id="category" name="category" className="field-input" value={form.category} onChange={set}>
              {CATEGORIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>
        </div>
        <div className="mt-4">
          <label htmlFor="description" className="mb-1.5 block text-sm font-medium">Description (optional)</label>
          <input id="description" name="description" className="field-input" placeholder="Condition, accessories included, serial number" value={form.description} onChange={set} maxLength={500} />
        </div>
        <button type="submit" className="btn-primary mt-4 sm:w-auto" disabled={busy || !form.name.trim()}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Plus className="h-4 w-4" aria-hidden />}
          Register on blockchain
        </button>
      </form>

      <ul className="mt-6 divide-y divide-paper-200 rounded-xl border border-paper-300 bg-white">
        {assets.length === 0 && (
          <li className="px-4 py-8 text-center text-[15px] text-ink-600">No assets yet. Register the first one above.</li>
        )}
        {assets.map((a) => {
          const s = STATUS[a.status];
          const mine = a.ownerId === user.id;
          return (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3.5">
              <div className="min-w-0">
                <p className="text-[15px] font-medium">
                  {a.name} <span className="ml-1 font-mono text-xs text-ink-400">{a.code}</span>
                </p>
                <p className="text-[13px] text-ink-600">
                  {a.category} · {mine ? 'Yours' : `Owner: ${a.ownerName}`}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${s.cls}`}>{s.label}</span>
                {user.role === 'ADMIN' && a.status === 'PENDING' && (
                  <button className="btn-quiet" disabled={busy} onClick={() => run(() => assetService.verify(a.id))}>
                    Verify ownership
                  </button>
                )}
                {a.status === 'AVAILABLE' && !mine && (
                  <button className="btn-quiet" disabled={busy} onClick={() => run(() => assetService.borrow(a.id, BORROW_DAYS))}>
                    Borrow for {BORROW_DAYS} days
                  </button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}