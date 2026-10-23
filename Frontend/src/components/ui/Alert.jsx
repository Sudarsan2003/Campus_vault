import { AlertCircle, CheckCircle2 } from 'lucide-react';

const styles = {
  error: { box: 'border-alert/30 bg-alert-soft text-alert', Icon: AlertCircle },
  success: { box: 'border-verified/30 bg-verified-soft text-verified', Icon: CheckCircle2 },
};

export default function Alert({ variant = 'error', children }) {
  const { box, Icon } = styles[variant];
  return (
    <div role={variant === 'error' ? 'alert' : 'status'} className={`flex items-start gap-2.5 rounded-lg border px-3.5 py-3 text-sm ${box}`}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  );
}