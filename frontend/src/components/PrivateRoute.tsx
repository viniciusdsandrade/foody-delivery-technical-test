import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/** Requires an authenticated session; guests are sent to /login, keeping the
 * intended destination so login can return to it. */
export default function PrivateRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  return isAuthenticated
    ? <Outlet />
    : <Navigate to="/login" replace
        state={{ from: location.pathname + location.search + location.hash }} />;
}
