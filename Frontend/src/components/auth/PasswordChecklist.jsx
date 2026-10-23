import { Check, Circle } from 'lucide-react';
import { passwordChecks } from '../../utils/validators.js';

export default function PasswordChecklist({ password }) {
  const checks = passwordChecks(password);
  return (
    <ul className="mt-2.5 grid grid-cols-1 gap-x-4 gap-y-1 sm:grid-cols-2" aria-label="Password requirements">
      {checks.map((c) => (
        <li key={c.id} className={`flex items-center gap-2 text-[13px] ${c.ok ? 'text-verified' : 'text-ink-600'}`}>
          {c.ok ? <Check className="h-3.5 w-3.5" aria-hidden /> : <Circle className="h-3 w-3 text-ink-300" aria-hidden />}
          <span>
            {c.label}
            <span className="sr-only">{c.ok ? ' (met)' : ' (not met)'}</span>
          </span>
        </li>
      ))}
    </ul>
  );
}