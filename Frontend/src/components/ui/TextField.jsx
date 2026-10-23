import { useId } from 'react';

export default function TextField({ label, error, hint, icon: Icon, trailing, className = '', ...props }) {
  const id = useId();
  const messageId = `${id}-msg`;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-ink-900">
        {label}
      </label>
      <div className="relative">
        {Icon && <Icon aria-hidden className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />}
        <input
          id={id}
          aria-invalid={!!error}
          aria-describedby={error || hint ? messageId : undefined}
          className={`field-input ${Icon ? 'pl-10' : ''} ${trailing ? 'pr-11' : ''}`}
          {...props}
        />
        {trailing && <div className="absolute inset-y-0 right-1.5 flex items-center">{trailing}</div>}
      </div>
      {(error || hint) && (
        <p id={messageId} className={`mt-1.5 text-sm ${error ? 'text-alert' : 'text-ink-600'}`}>
          {error || hint}
        </p>
      )}
    </div>
  );
}