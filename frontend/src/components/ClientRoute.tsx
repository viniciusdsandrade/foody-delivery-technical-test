import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/**
 * CLIENT-only area: admins cannot place orders (the API answers 403),
 * so they are sent back to the orders list instead of a dead-end form.
 */
export default function ClientRoute() {
  const { isAdmin } = useAuth();
  return isAdmin ? <Navigate to="/orders" replace /> : <Outlet />;
}
