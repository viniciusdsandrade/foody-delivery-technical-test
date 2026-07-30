import type { OrderStatus } from '../types';

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  RECEBIDO: 'Recebido',
  EM_PREPARO: 'Em preparo',
  SAIU_PARA_ENTREGA: 'Saiu para entrega',
  ENTREGUE: 'Entregue',
  CANCELADO: 'Cancelado',
};

export const ORDER_STATUS_BADGE: Record<OrderStatus, string> = {
  RECEBIDO: 'bg-blue-100 text-blue-700',
  EM_PREPARO: 'bg-amber-100 text-amber-700',
  SAIU_PARA_ENTREGA: 'bg-violet-100 text-violet-700',
  ENTREGUE: 'bg-emerald-100 text-emerald-700',
  CANCELADO: 'bg-red-100 text-red-700',
};

/** Valid transitions, mirroring the backend state machine (the API stays the source of truth). */
export const VALID_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  RECEBIDO: ['EM_PREPARO', 'CANCELADO'],
  EM_PREPARO: ['SAIU_PARA_ENTREGA', 'CANCELADO'],
  SAIU_PARA_ENTREGA: ['ENTREGUE'],
  ENTREGUE: [],
  CANCELADO: [],
};

export const ORDER_STATUSES = Object.keys(ORDER_STATUS_LABELS) as OrderStatus[];
