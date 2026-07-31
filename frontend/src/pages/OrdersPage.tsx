import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import StatusBadge from '../components/StatusBadge';
import { useAuth } from '../hooks/useAuth';
import { errorMessage } from '../services/apiError';
import { listOrders } from '../services/orderService';
import type { Order, OrderStatus } from '../types';
import { formatCurrency, formatDateTime } from '../utils/format';
import { ORDER_STATUS_LABELS, ORDER_STATUSES } from '../utils/orderStatus';

export default function OrdersPage() {
  const { isClient } = useAuth();
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('');
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setOrders(null);
    setError(null);
    listOrders(statusFilter || undefined)
      .then((data) => {
        if (!cancelled) {
          setOrders(data);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(errorMessage(err, 'Não foi possível carregar os pedidos.'));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [statusFilter]);

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-800">Pedidos</h1>
        <div className="flex items-center gap-3">
          <select
            aria-label="Filtrar por status"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value as OrderStatus | '')}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-orange-500"
          >
            <option value="">Todos os status</option>
            {ORDER_STATUSES.map((status) => (
              <option key={status} value={status}>
                {ORDER_STATUS_LABELS[status]}
              </option>
            ))}
          </select>
          {isClient && (
            <Link
              to="/orders/new"
              className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-orange-700"
            >
              Novo pedido
            </Link>
          )}
        </div>
      </div>

      {error && (
        <div role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}

      {!error && orders === null && (
        <p className="py-12 text-center text-sm text-slate-500">Carregando pedidos…</p>
      )}

      {!error && orders !== null && orders.length === 0 && (
        <p className="rounded-xl bg-white py-12 text-center text-sm text-slate-500 shadow-sm">
          Nenhum pedido encontrado{statusFilter ? ' para este status' : ''}.
        </p>
      )}

      <div className="space-y-3">
        {!error && orders?.map((order) => (
          <Link
            key={order.id}
            to={`/orders/${order.id}`}
            className="block rounded-xl bg-white p-4 shadow-sm transition hover:shadow"
          >
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <span className="font-semibold text-slate-800">Pedido #{order.id}</span>
                <span className="ml-2 truncate text-sm text-slate-500">{order.customerName}</span>
              </div>
              <StatusBadge status={order.status} />
            </div>
            <div className="mt-2 flex items-center justify-between text-sm text-slate-600">
              <span>
                {order.items.length} {order.items.length === 1 ? 'item' : 'itens'} ·{' '}
                {formatDateTime(order.createdAt)}
              </span>
              <span className="font-semibold text-slate-800">{formatCurrency(order.total)}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
