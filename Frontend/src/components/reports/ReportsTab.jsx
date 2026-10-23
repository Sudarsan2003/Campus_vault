import { useEffect, useState } from 'react';
import { ImagePlus, Loader2, Sparkles } from 'lucide-react';
import AuthImage from './AuthImage.jsx';
import { reportService } from '../../services/reportService.js';
import { parseApiError } from '../../lib/api.js';

const CATEGORIES = ['Book', 'Laptop', 'Charger', 'Calculator', 'Lab equipment', 'ID card', 'Keys', 'Bag', 'Other'];

const STATUS = {
  OPEN: { label: 'Open', cls: 'bg-pending/15 text-ink-800' },
  MATCHED: { label: 'Matched', cls: 'bg-chain-soft text-chain' },
  RETURNED: { label: 'Returned', cls: 'bg-verified/15 text-verified' },
};

const STRENGTH = {
  Strong: 'bg-verified/15 text-verified',
  Likely: 'bg-chain-soft text-chain',
  Possible: 'bg-pending/15 text-ink-800',
};

// Local date as yyyy-mm-dd (toISOString would use UTC and can be a day off).
const today = () => {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 10);
};

const blank = () => ({ kind: 'LOST', title: '', category: CATEGORIES[0], description: '', location: '', eventDate: today() });

export default function ReportsTab({ reports, user, run, busy }) {
  const [form, setForm] = useState(blank);
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [fileKey, setFileKey] = useState(0); // changing this resets the file input
  const [filter, setFilter] = useState('ALL');

  const set = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  useEffect(() => {
    if (!file) return setPreview(null);
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const canSubmit = form.title.trim() && form.location.trim() && file && !busy;

  const submit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    const fd = new FormData();
    Object.entries(form).forEach(([k, v]) => fd.append(k, typeof v === 'string' ? v.trim() : v));
    fd.append('image', file);
    const ok = await run(() => reportService.create(fd));
    if (ok) {
      setForm(blank());
      setFile(null);
      setFileKey((k) => k + 1);
    }
  };

  const visible = reports.filter((r) => filter === 'ALL' || r.kind === filter);
  const isLost = form.kind === 'LOST';

  return (
    <div>
      <form onSubmit={submit} className="rounded-xl border border-paper-300 bg-white p-4">
        <p className="text-[15px] font-medium">Report a lost or found item</p>
        <p className="mt-0.5 text-[13px] text-ink-600">
          Your report and the photo's fingerprint are written to the blockchain, so the report can't be quietly changed later.
        </p>

        <div className="mt-4 inline-flex rounded-lg border border-paper-300 p-0.5" role="radiogroup" aria-label="Report type">
          {[['LOST', 'I lost something'], ['FOUND', 'I found something']].map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={form.kind === value}
              onClick={() => setForm((f) => ({ ...f, kind: value }))}
              className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                form.kind === value ? 'bg-ink-900 text-white' : 'text-ink-600 hover:text-ink-900'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="mt-4 grid gap-4 sm:grid-cols-[2fr_1fr]">
          <div>
            <label htmlFor="r-title" className="mb-1.5 block text-sm font-medium">What is it?</label>
            <input id="r-title" name="title" className="field-input" placeholder="Black HP laptop with stickers" value={form.title} onChange={set} maxLength={120} />
          </div>
          <div>
            <label htmlFor="r-category" className="mb-1.5 block text-sm font-medium">Category</label>
            <select id="r-category" name="category" className="field-input" value={form.category} onChange={set}>
              {CATEGORIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="mt-4 grid gap-4 sm:grid-cols-[2fr_1fr]">
          <div>
            <label htmlFor="r-location" className="mb-1.5 block text-sm font-medium">{isLost ? 'Where did you lose it?' : 'Where did you find it?'}</label>
            <input id="r-location" name="location" className="field-input" placeholder="Library, 2nd floor" value={form.location} onChange={set} maxLength={120} />
          </div>
          <div>
            <label htmlFor="r-date" className="mb-1.5 block text-sm font-medium">{isLost ? 'Date lost' : 'Date found'}</label>
            <input id="r-date" name="eventDate" type="date" className="field-input" value={form.eventDate} max={today()} onChange={set} />
          </div>
        </div>

        <div className="mt-4">
          <label htmlFor="r-desc" className="mb-1.5 block text-sm font-medium">Details (optional)</label>
          <input id="r-desc" name="description" className="field-input" placeholder="Colour, marks, what's inside. Keep one detail back to verify the owner." value={form.description} onChange={set} maxLength={500} />
        </div>

        <div className="mt-4">
          <label htmlFor="r-image" className="mb-1.5 block text-sm font-medium">Photo</label>
          <div className="flex items-center gap-4">
            {preview ? (
              <img src={preview} alt="Selected item preview" className="h-20 w-20 rounded-lg border border-paper-300 object-cover" />
            ) : (
              <div className="flex h-20 w-20 items-center justify-center rounded-lg border border-dashed border-paper-300 text-ink-400">
                <ImagePlus className="h-5 w-5" aria-hidden />
              </div>
            )}
            <div>
              <input
                key={fileKey}
                id="r-image"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
                className="block text-sm text-ink-600 file:mr-3 file:rounded-lg file:border file:border-paper-300 file:bg-white file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-ink-900 hover:file:bg-paper-200"
              />
              <p className="mt-1 text-[13px] text-ink-600">JPG, PNG or WebP, up to 5 MB.</p>
            </div>
          </div>
        </div>

        <button type="submit" className="btn-primary mt-5 sm:w-auto" disabled={!canSubmit}>
          {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          Submit report
        </button>
      </form>

      <div className="mt-6 flex gap-2" role="group" aria-label="Filter reports">
        {[['ALL', 'All'], ['LOST', 'Lost'], ['FOUND', 'Found']].map(([value, label]) => (
          <button
            key={value}
            onClick={() => setFilter(value)}
            aria-pressed={filter === value}
            className={`rounded-full border px-3 py-1 text-sm font-medium transition-colors ${
              filter === value ? 'border-ink-900 bg-ink-900 text-white' : 'border-paper-300 bg-white text-ink-600 hover:bg-paper-200'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <ul className="mt-4 space-y-3">
        {visible.length === 0 && (
          <li className="rounded-xl border border-paper-300 bg-white px-4 py-8 text-center text-[15px] text-ink-600">
            No reports yet. Report the first one above.
          </li>
        )}
        {visible.map((r) => (
          <ReportCard key={r.id} report={r} reports={reports} user={user} run={run} busy={busy} />
        ))}
      </ul>
    </div>
  );
}

function ReportCard({ report: r, reports, user, run, busy }) {
  const [pick, setPick] = useState('');
  const [suggestions, setSuggestions] = useState(null); // null = not searched yet
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');

  const mine = r.reporterId === user.id;
  const admin = user.role === 'ADMIN';
  const s = STATUS[r.status];

  // Open found reports this person could claim, same category first (manual fallback).
  const candidates = reports
    .filter((x) => x.kind === 'FOUND' && x.status === 'OPEN' && x.reporterId !== r.reporterId)
    .sort((a, b) => (b.category === r.category) - (a.category === r.category));

  const canClaim = r.kind === 'LOST' && r.status === 'OPEN' && (mine || admin);
  const canConfirm = r.kind === 'FOUND' && r.status === 'MATCHED' && (mine || admin);

  const findMatches = async () => {
    setSearching(true);
    setSearchError('');
    try {
      setSuggestions(await reportService.suggestions(r.id));
    } catch (err) {
      setSuggestions(null);
      setSearchError(parseApiError(err).message);
    } finally {
      setSearching(false);
    }
  };

  return (
    <li className="rounded-xl border border-paper-300 bg-white p-4">
      <div className="flex gap-4">
        <AuthImage id={r.id} alt={r.title} className="h-24 w-24 shrink-0 rounded-lg border border-paper-300" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <p className="text-[15px] font-medium">
              {r.title} <span className="ml-1 font-mono text-xs text-ink-400">{r.code}</span>
            </p>
            <div className="flex items-center gap-1.5">
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${r.kind === 'LOST' ? 'bg-alert/10 text-alert' : 'bg-verified/15 text-verified'}`}>
                {r.kind === 'LOST' ? 'Lost' : 'Found'}
              </span>
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${s.cls}`}>{s.label}</span>
            </div>
          </div>
          <p className="mt-0.5 text-[13px] text-ink-600">
            {r.category} · {r.location} · {new Date(r.eventDate).toLocaleDateString()} · {mine ? 'You' : r.reporterName}
          </p>
          {r.description && <p className="mt-1.5 text-[14px] text-ink-800">{r.description}</p>}
          {r.matchedWithCode && (
            <p className="mt-1.5 text-[13px] text-ink-600">
              Matched with <span className="font-mono">{r.matchedWithCode}</span>
              {r.status === 'MATCHED' && r.kind === 'LOST' && mine && ' · waiting for the finder to confirm the handover'}
            </p>
          )}
        </div>
      </div>

      {canClaim && (
        <div className="mt-3 border-t border-paper-200 pt-3">
          <div className="flex flex-wrap items-center gap-3">
            <button className="btn-quiet" disabled={searching || busy} onClick={findMatches}>
              {searching ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Sparkles className="h-4 w-4" aria-hidden />}
              {suggestions ? 'Search again' : 'Find possible matches'}
            </button>
            <p className="text-[13px] text-ink-600">Compares your photo with open found reports.</p>
          </div>

          <div aria-live="polite">
            {searchError && <p className="mt-3 text-[14px] text-alert">{searchError}</p>}

            {suggestions && suggestions.length === 0 && (
              <p className="mt-3 text-[14px] text-ink-600">No similar found items yet. Check back when more are reported.</p>
            )}

            {suggestions && suggestions.length > 0 && (
              <ul className="mt-3 space-y-2">
                {suggestions.map((m) => (
                  <li key={m.report.id} className="flex flex-wrap items-center gap-3 rounded-lg border border-paper-200 p-2.5">
                    <AuthImage id={m.report.id} alt={m.report.title} className="h-14 w-14 shrink-0 rounded-md border border-paper-300" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[14px] font-medium">
                        {m.report.title} <span className="ml-1 font-mono text-xs text-ink-400">{m.report.code}</span>
                      </p>
                      <p className="truncate text-[13px] text-ink-600">
                        {m.report.category} · {m.report.location} · {new Date(m.report.eventDate).toLocaleDateString()}
                      </p>
                    </div>
                    <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${STRENGTH[m.strength]}`}>
                      {m.similarity}% similar
                    </span>
                    <button className="btn-quiet" disabled={busy} onClick={() => run(() => reportService.match(r.id, m.report.id))}>
                      This is mine
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {candidates.length > 0 && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <label htmlFor={`pick-${r.id}`} className="text-[13px] text-ink-600">Or choose manually:</label>
              <select id={`pick-${r.id}`} className="field-input max-w-xs" value={pick} onChange={(e) => setPick(e.target.value)}>
                <option value="">Select a found item</option>
                {candidates.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.code} · {c.title} ({c.category})
                  </option>
                ))}
              </select>
              <button className="btn-quiet" disabled={busy || !pick} onClick={() => run(() => reportService.match(r.id, Number(pick)))}>
                This is mine
              </button>
            </div>
          )}
        </div>
      )}

      {canConfirm && (
        <div className="mt-3 border-t border-paper-200 pt-3">
          <button className="btn-quiet" disabled={busy} onClick={() => run(() => reportService.confirmReturn(r.id))}>
            Confirm item handed back
          </button>
        </div>
      )}
    </li>
  );
}