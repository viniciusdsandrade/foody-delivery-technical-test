import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import axios from 'axios';
import StatusBadge from '../components/StatusBadge';
import { useAuth } from '../hooks/useAuth';
import { errorMessage } from '../services/apiError';
import { getOrder, getOrderHistory, updateOrderStatus } from '../services/orderService';
import type { Order, OrderStatus, StatusHistoryEntry } from '../types';
import { formatCurrency, formatDateTime } from '../utils/format';
import { ORDER_STATUS_LABELS, VALID_TRANSITIONS } from '../utils/orderStatus';

function timelineText(entry: StatusHistoryEntry): string {
  if (entry.fromStatus === null) {
    return `Pedido criado (${ORDER_STATUS_LABELS[entry.toStatus]})`;
  }
  return `${ORDER_STATUS_LABELS[entry.fromStatus]} → ${ORDER_STATUS_LABELS[entry.toStatus]}`;
}

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  const { isAdmin } = useAuth();
  const [order, setOrder] = useState<Order | null>(null);
  const [history, setHistory] = useState<StatusHistoryEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [updating, setUpdating] = useState<OrderStatus | null>(null);

  const load = useCallback(async () => {
    try {
      const [orderData, historyData] = await Promise.all([getOrder(orderId), getOrderHistory(orderId)]);
      setOrder(orderData);
      setHistory(historyData);
    } catch (err) {
      setError(
        axios.isAxiosError(err) && err.response?.status === 404
          ? 'Pedido não encontrado.'
          : errorMessage(err, 'Não foi possível carregar o pedido.'),
      );
    }
  }, [orderId]);

  useEffect(() => {
    setOrder(null);
    setError(null);
    void load();
  }, [load]);

  async function handleTransition(status: OrderStatus) {
    if (status === 'CANCELADO' && !window.confirm('Tem certeza que deseja cancelar este pedido?')) {
      return;
    }
    setActionError(null);
    setUpdating(status);
    try {
      const updated = await updateOrderStatus(orderId, status);
      setOrder(updated);
      setHistory(await getOrderHistory(orderId));
    } catch (err) {
      setActionError(errorMessage(err, 'Não foi possível atualizar o status.'));
    } finally {
      setUpdating(null);
    }
  }

  if (error) {
    return (
      <div className="rounded-xl bg-white p-8 text-center shadow-sm">
        <p className="mb-4 text-sm text-red-700">{error}</p>
        <Link to="/orders" className="text-sm font-medium text-orange-600 hover:underline">
          Voltar para pedidos
        </Link>
      </div>
    );
  }

  if (!order) {
    return <p className="py-12 text-center text-sm text-slate-500">Carregando pedido…</p>;
  }

  const transitions = VALID_TRANSITIONS[order.status];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <Link to="/orders" className="text-sm font-medium text-slate-500 hover:text-slate-800">
          ← Voltar
        </Link>
      </div>

      <section className="rounded-xl bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-slate-800">Pedido #{order.id}</h1>
            <p className="text-sm text-slate-500">
              {order.customerName} · criado em {formatDateTime(order.createdAt)}
            </p>
          </div>
          <StatusBadge status={order.status} />
        </div>

        {isAdmin && transitions.length > 0 && (
          <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
            <span className="text-sm font-medium text-slate-600">Avançar status:</span>
            {transitions.map((status) => (
              <button
                key={status}
                type="button"
                disabled={updating !== null}
                onClick={() => handleTransition(status)}
                className={
                  status === 'CANCELADO'
                    ? 'rounded-lg border border-red-600 px-3 py-1.5 text-sm font-medium text-red-600 transition hover:bg-red-50 disabled:opacity-60'
                    : 'rounded-lg bg-orange-600 px-3 py-1.5 text-sm font-semibold text-white transition hover:bg-orange-700 disabled:opacity-60'
                }
              >
                {updating === status ? 'Atualizando…' : ORDER_STATUS_LABELS[status]}
              </button>
            ))}
          </div>
        )}
        {actionError && (
          <div className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{actionError}</div>
        )}
      </section>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section className="rounded-xl bg-white p-5 shadow-sm">
          <h2 className="mb-4 font-semibold text-slate-800">Itens</h2>
          <ul className="divide-y divide-slate-100">
            {order.items.map((item) => (
              <li key={item.id} className="flex items-center justify-between py-2 text-sm">
                <span className="text-slate-700">
                  {item.quantity}× {item.name}
                </span>
                <span className="text-slate-600">{formatCurrency(item.quantity * item.unitPrice)}</span>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex items-center justify-between border-t border-slate-200 pt-3">
            <span className="font-semibold text-slate-800">Total</span>
            <span className="font-bold text-slate-900">{formatCurrency(order.total)}</span>
          </div>

          <h2 className="mb-2 mt-6 font-semibold text-slate-800">Endereço de entrega</h2>
          <p className="text-sm text-slate-600">
            {order.address.street}, {order.address.number}
            {order.address.complement ? ` — ${order.address.complement}` : ''}
            <br />
            {order.address.district} · {order.address.city}/{order.address.state}
            <br />
            CEP {order.address.zipCode}
          </p>
        </section>

        <section className="rounded-xl bg-white p-5 shadow-sm">
          <h2 className="mb-4 font-semibold text-slate-800">Histórico de status</h2>
          <ol className="relative space-y-4 border-l-2 border-slate-200 pl-4">
            {history.map((entry, index) => (
              <li key={index} className="relative">
                <span className="absolute -left-[21px] top-1 h-2.5 w-2.5 rounded-full bg-orange-500" />
                <p className="text-sm font-medium text-slate-800">{timelineText(entry)}</p>
                <p className="text-xs text-slate-500">
                  por {entry.changedBy.name} · {formatDateTime(entry.changedAt)}
                </p>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </div>
  );
}
