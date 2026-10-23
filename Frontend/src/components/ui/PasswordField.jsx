import { useState } from 'react';
import { Eye, EyeOff, Lock } from 'lucide-react';
import TextField from './TextField.jsx';

export default function PasswordField(props) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      icon={Lock}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          className="rounded-md p-2 text-ink-600 hover:bg-paper-200 hover:text-ink-900"
        >
          {visible ? <EyeOff className="h-4 w-4" aria-hidden /> : <Eye className="h-4 w-4" aria-hidden />}
        </button>
      }
      {...props}
    />
  );
}