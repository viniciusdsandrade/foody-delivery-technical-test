import type { OrderStatus } from '../types';
import { ORDER_STATUS_BADGE, ORDER_STATUS_LABELS } from '../utils/orderStatus';

export default function StatusBadge({ status }: { status: OrderStatus }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${ORDER_STATUS_BADGE[status]}`}>
      {ORDER_STATUS_LABELS[status]}
    </span>
  );
}
