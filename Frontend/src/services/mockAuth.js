const USERS_KEY = 'cv_mock_users';
const delay = (ms = 600) => new Promise((r) => setTimeout(r, ms));

const seed = [
  { id: 1, fullName: 'Demo Student', email: 'student@campus.edu', studentId: 'CS24M001', department: 'CSE', role: 'STUDENT', password: 'Student@123' },
  { id: 2, fullName: 'Campus Admin', email: 'admin@campus.edu', studentId: 'ADMIN-01', department: 'CSE', role: 'ADMIN', password: 'Admin@123' },
];

const load = () => {
  try {
    const raw = localStorage.getItem(USERS_KEY);
    if (raw) return JSON.parse(raw);
  } catch { /* fall through */ }
  localStorage.setItem(USERS_KEY, JSON.stringify(seed));
  return [...seed];
};
const save = (users) => localStorage.setItem(USERS_KEY, JSON.stringify(users));
const publicUser = ({ password, ...u }) => u;
const fail = (status, message, errors = {}) => Promise.reject({ response: { status, data: { message, errors } } });

export const mockAuth = {
  async login({ email, password }) {
    await delay();
    const user = load().find((u) => u.email.toLowerCase() === email.toLowerCase());
    if (!user || user.password !== password) return fail(401, 'Email or password is incorrect.');
    return { token: `mock.${btoa(user.email)}.${Date.now()}`, user: publicUser(user) };
  },

  async register({ fullName, email, studentId, department, password }) {
    await delay(800);
    const users = load();
    const errors = {};
    if (users.some((u) => u.email.toLowerCase() === email.toLowerCase())) errors.email = 'This email is already registered.';
    if (users.some((u) => u.studentId.toLowerCase() === studentId.toLowerCase())) errors.studentId = 'This student ID is already registered.';
    if (Object.keys(errors).length) return fail(409, 'Registration failed. Fix the highlighted fields.', errors);
    const user = { id: Date.now(), fullName, email, studentId, department, role: 'STUDENT', password };
    save([...users, user]);
    return { message: 'Account created.', user: publicUser(user) };
  },

  async me(token) {
    await delay(200);
    if (!token?.startsWith('mock.')) return fail(401, 'Session expired.');
    const email = atob(token.split('.')[1]);
    const user = load().find((u) => u.email === email);
    return user ? publicUser(user) : fail(401, 'Session expired.');
  },
};