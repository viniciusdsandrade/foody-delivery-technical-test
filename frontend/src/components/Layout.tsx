import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const navClass = ({ isActive }: { isActive: boolean }) =>
  `text-sm font-medium ${isActive ? 'text-orange-600' : 'text-slate-500 hover:text-slate-800'}`;

export default function Layout() {
  const { user, isClient, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="bg-white shadow-sm">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-6">
            <NavLink to="/orders" className="text-lg font-bold text-orange-600">
              Foody Tracker
            </NavLink>
            <nav className="flex items-center gap-4">
              <NavLink to="/orders" end className={navClass}>
                Pedidos
              </NavLink>
              {isClient && (
                <NavLink to="/orders/new" className={navClass}>
                  Novo pedido
                </NavLink>
              )}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-slate-600">{user?.name}</span>
            <span className="rounded-full bg-orange-100 px-2 py-0.5 text-xs font-semibold text-orange-700">
              {user?.role === 'ADMIN' ? 'Admin' : 'Cliente'}
            </span>
            <button
              type="button"
              onClick={handleLogout}
              className="text-sm font-medium text-slate-500 hover:text-red-600"
            >
              Sair
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
