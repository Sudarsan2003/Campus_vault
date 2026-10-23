import { useId } from 'react';
import { ChevronDown } from 'lucide-react';

export default function SelectField({ label, error, icon: Icon, options, placeholder, className = '', ...props }) {
  const id = useId();
  const messageId = `${id}-msg`;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink-900">
        {label}
      </label>
      <div className="relative">
        {Icon && <Icon aria-hidden className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />}
        <select
          id={id}
          aria-invalid={!!error}
          aria-describedby={error ? messageId : undefined}
          className={`field-input appearance-none pr-10 ${Icon ? 'pl-10' : ''} ${props.value ? '' : 'text-ink-400'}`}
          {...props}
        >
          <option value="" disabled>
            {placeholder}
          </option>
          {options.map((o) => (
            <option key={o.value} value={o.value} className="text-ink-900">
              {o.label}
            </option>
          ))}
        </select>
        <ChevronDown aria-hidden className="pointer-events-none absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />
      </div>
      {error && (
        <p id={messageId} className="mt-1.5 text-sm text-alert">
          {error}
        </p>
      )}
    </div>
  );
}