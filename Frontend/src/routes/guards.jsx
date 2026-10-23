import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';

function Splash() {
  return (
    <div className="grid min-h-screen place-items-center bg-paper" role="status" aria-label="Loading">
      <Loader2 className="h-6 w-6 animate-spin text-chain" aria-hidden />
    </div>
  );
}

export function ProtectedRoute() {
  const { isAuthenticated, initialising } = useAuth();
  const location = useLocation();
  if (initialising) return <Splash />;
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

export function GuestRoute() {
  const { isAuthenticated, initialising } = useAuth();
  if (initialising) return <Splash />;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return <Outlet />;
}