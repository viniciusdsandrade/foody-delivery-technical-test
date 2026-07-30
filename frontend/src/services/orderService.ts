import api from './api';
import type { Address, Order, OrderStatus, StatusHistoryEntry } from '../types';

export interface OrderItemPayload {
  name: string;
  quantity: number;
  unitPrice: number;
}

export interface OrderPayload {
  customerName: string;
  address: Address;
  items: OrderItemPayload[];
}

export async function listOrders(status?: OrderStatus): Promise<Order[]> {
  const { data } = await api.get<Order[]>('/orders', { params: status ? { status } : {} });
  return data;
}

export async function createOrder(payload: OrderPayload): Promise<Order> {
  const { data } = await api.post<Order>('/orders', payload);
  return data;
}

export async function getOrder(id: number): Promise<Order> {
  const { data } = await api.get<Order>(`/orders/${id}`);
  return data;
}

export async function getOrderHistory(id: number): Promise<StatusHistoryEntry[]> {
  const { data } = await api.get<StatusHistoryEntry[]>(`/orders/${id}/history`);
  return data;
}

export async function updateOrderStatus(id: number, status: OrderStatus): Promise<Order> {
  const { data } = await api.patch<Order>(`/orders/${id}/status`, { status });
  return data;
}
