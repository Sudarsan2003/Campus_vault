import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Loader2, Mail } from 'lucide-react';
import AuthShell from '../components/auth/AuthShell.jsx';
import TextField from '../components/ui/TextField.jsx';
import PasswordField from '../components/ui/PasswordField.jsx';
import Alert from '../components/ui/Alert.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { USE_MOCK, parseApiError } from '../lib/api.js';
import { validateLogin } from '../utils/validators.js';
import { focusFirstInvalid } from '../utils/focusFirstInvalid.js';

const DEMO_ACCOUNTS = [
  { label: 'Student', email: 'student@campus.edu', password: 'Student@123' },
  { label: 'Admin', email: 'admin@campus.edu', password: 'Admin@123' },
];

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = location.state?.from?.pathname || '/dashboard';

  const [values, setValues] = useState({ email: location.state?.email || '', password: '' });
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);

  const errors = validateLogin(values);
  const shown = (field) => (submitted || touched[field] ? errors[field] : undefined);

  const onChange = (e) => {
    setValues((v) => ({ ...v, [e.target.name]: e.target.value }));
    setFormError('');
  };
  const onBlur = (e) => setTouched((t) => ({ ...t, [e.target.name]: true }));

  const onSubmit = async (e) => {
    e.preventDefault();
    setSubmitted(true);
    if (Object.keys(errors).length) return focusFirstInvalid();

    setLoading(true);
    setFormError('');
    try {
      await login({ email: values.email.trim(), password: values.password });
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setFormError(parseApiError(err).message);
      setLoading(false);
    }
  };

  return (
    <AuthShell
      title="Sign in"
      subtitle="Manage your assets and view their blockchain history."
      footer={
        <>
          New to CampusVault?{' '}
          <Link to="/register" className="font-semibold text-chain hover:text-chain-dark hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {location.state?.registered && <Alert variant="success">Account created. Sign in to continue.</Alert>}
        {formError && <Alert>{formError}</Alert>}

        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          icon={Mail}
          placeholder="you@campus.edu"
          value={values.email}
          onChange={onChange}
          onBlur={onBlur}
          error={shown('email')}
        />
        <PasswordField
          label="Password"
          name="password"
          autoComplete="current-password"
          placeholder="Enter your password"
          value={values.password}
          onChange={onChange}
          onBlur={onBlur}
          error={shown('password')}
        />

        <button type="submit" className="btn-primary" disabled={loading}>
          {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      {USE_MOCK && (
        <div className="mt-8 rounded-lg border border-dashed border-paper-300 bg-white/60 p-4">
          <p className="text-sm font-medium text-ink-900">Demo mode</p>
          <p className="mt-0.5 text-[13px] text-ink-600">The backend isn't connected yet. Use a demo account to explore.</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.label}
                type="button"
                className="btn-quiet"
                onClick={() => {
                  setValues({ email: a.email, password: a.password });
                  setFormError('');
                }}
              >
                Fill {a.label.toLowerCase()} login
              </button>
            ))}
          </div>
        </div>
      )}
    </AuthShell>
  );
}