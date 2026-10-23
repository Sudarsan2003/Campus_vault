export default function Logo({ className = '', dark = false }) {
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <svg width="30" height="30" viewBox="0 0 32 32" aria-hidden>
        <rect width="32" height="32" rx="7" fill={dark ? '#FFFFFF' : '#0E1B33'} fillOpacity={dark ? 0.08 : 1} />
        <rect x="7" y="7" width="8" height="8" rx="1.5" fill="#5B78FF" />
        <rect x="17" y="17" width="8" height="8" rx="1.5" fill="#5B78FF" />
        <rect x="17" y="7" width="8" height="8" rx="1.5" fill="#8A97B1" />
        <rect x="7" y="17" width="8" height="8" rx="1.5" fill="#8A97B1" />
      </svg>
      <span className={`text-lg font-semibold tracking-tight ${dark ? 'text-white' : 'text-ink-900'}`}>CampusVault</span>
    </div>
  );
}