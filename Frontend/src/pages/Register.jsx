import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Building2, Database, Fingerprint, Loader2, Mail, User } from 'lucide-react';
import AuthShell from '../components/auth/AuthShell.jsx';
import PasswordChecklist from '../components/auth/PasswordChecklist.jsx';
import TextField from '../components/ui/TextField.jsx';
import PasswordField from '../components/ui/PasswordField.jsx';
import SelectField from '../components/ui/SelectField.jsx';
import Alert from '../components/ui/Alert.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { parseApiError } from '../lib/api.js';
import { DEPARTMENTS, validateRegister } from '../utils/validators.js';
import { focusFirstInvalid } from '../utils/focusFirstInvalid.js';

const initial = { fullName: '', email: '', studentId: '', department: '', password: '', confirmPassword: '' };

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [values, setValues] = useState(initial);
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [serverErrors, setServerErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);

  const errors = validateRegister(values);
  const shown = (field) => serverErrors[field] || (submitted || touched[field] ? errors[field] : undefined);

  const onChange = (e) => {
    const { name, value } = e.target;
    setValues((v) => ({ ...v, [name]: value }));
    setServerErrors((s) => ({ ...s, [name]: undefined }));
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
      await register({
        fullName: values.fullName.trim(),
        email: values.email.trim().toLowerCase(),
        studentId: values.studentId.trim().toUpperCase(),
        department: values.department,
        password: values.password,
      });
      navigate('/login', { replace: true, state: { registered: true, email: values.email.trim().toLowerCase() } });
    } catch (err) {
      const { message, fieldErrors } = parseApiError(err);
      setFormError(message);
      setServerErrors(fieldErrors);
      setLoading(false);
      focusFirstInvalid();
    }
  };

  return (
    <AuthShell
      title="Create your account"
      subtitle="Register with your campus details to lend, borrow and report items."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-chain hover:text-chain-dark hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {formError && <Alert>{formError}</Alert>}

        <TextField label="Full name" name="fullName" autoComplete="name" icon={User} placeholder="Ananya Rao" value={values.fullName} onChange={onChange} onBlur={onBlur} error={shown('fullName')} />

        <TextField label="Campus email" name="email" type="email" autoComplete="email" icon={Mail} placeholder="you@campus.edu" value={values.email} onChange={onChange} onBlur={onBlur} error={shown('email')} />

        <div className="grid gap-5 sm:grid-cols-2">
          <TextField label="Student or staff ID" name="studentId" autoComplete="off" icon={Fingerprint} placeholder="CS24M001" value={values.studentId} onChange={onChange} onBlur={onBlur} error={shown('studentId')} />
          <SelectField label="Department" name="department" icon={Building2} placeholder="Select" options={DEPARTMENTS} value={values.department} onChange={onChange} onBlur={onBlur} error={shown('department')} />
        </div>

        <div>
          <PasswordField label="Password" name="password" autoComplete="new-password" placeholder="Create a password" value={values.password} onChange={onChange} onBlur={onBlur} error={shown('password')} />
          <PasswordChecklist password={values.password} />
        </div>

        <PasswordField label="Confirm password" name="confirmPassword" autoComplete="new-password" placeholder="Re-enter your password" value={values.confirmPassword} onChange={onChange} onBlur={onBlur} error={shown('confirmPassword')} />

        <div className="flex items-start gap-2.5 rounded-lg bg-chain-soft px-3.5 py-3 text-[13px] leading-relaxed text-ink-800">
          <Database className="mt-0.5 h-4 w-4 shrink-0 text-chain" aria-hidden />
          <p>Your account details are stored off-chain. Blockchain records begin when you register an asset or borrow one.</p>
        </div>

        <button type="submit" className="btn-primary" disabled={loading}>
          {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          {loading ? 'Creating account…' : 'Create account'}
        </button>
      </form>
    </AuthShell>
  );
}