export const DEPARTMENTS = [
  { value: 'CSE', label: 'Computer Science & Engineering' },
  { value: 'IT', label: 'Information Technology' },
  { value: 'ECE', label: 'Electronics & Communication' },
  { value: 'EEE', label: 'Electrical & Electronics' },
  { value: 'MECH', label: 'Mechanical Engineering' },
  { value: 'CIVIL', label: 'Civil Engineering' },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const STUDENT_ID_RE = /^[A-Za-z0-9-]{4,20}$/;

export const passwordChecks = (pw = '') => [
  { id: 'len', label: 'At least 8 characters', ok: pw.length >= 8 },
  { id: 'case', label: 'Upper and lower case letters', ok: /[a-z]/.test(pw) && /[A-Z]/.test(pw) },
  { id: 'num', label: 'A number', ok: /\d/.test(pw) },
  { id: 'sym', label: 'A symbol', ok: /[^A-Za-z0-9]/.test(pw) },
];

export const validateLogin = ({ email, password }) => {
  const e = {};
  if (!email.trim()) e.email = 'Enter your email address.';
  else if (!EMAIL_RE.test(email.trim())) e.email = 'Enter a valid email address.';
  if (!password) e.password = 'Enter your password.';
  return e;
};

export const validateRegister = (v) => {
  const e = {};
  if (v.fullName.trim().length < 2) e.fullName = 'Enter your full name.';
  if (!v.email.trim()) e.email = 'Enter your email address.';
  else if (!EMAIL_RE.test(v.email.trim())) e.email = 'Enter a valid email address.';
  if (!v.studentId.trim()) e.studentId = 'Enter your student or staff ID.';
  else if (!STUDENT_ID_RE.test(v.studentId.trim())) e.studentId = 'Use 4–20 letters, numbers or hyphens.';
  if (!v.department) e.department = 'Select your department.';
  if (!passwordChecks(v.password).every((c) => c.ok)) e.password = 'Password does not meet all requirements.';
  if (!v.confirmPassword) e.confirmPassword = 'Re-enter your password.';
  else if (v.confirmPassword !== v.password) e.confirmPassword = 'Passwords do not match.';
  return e;
};