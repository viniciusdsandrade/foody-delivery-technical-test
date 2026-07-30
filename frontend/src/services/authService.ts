import api from './api';
import type { LoginResponse, User } from '../types';

export async function login(credentials: { email: string; password: string }): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', credentials);
  return data;
}

export async function register(payload: { name: string; email: string; password: string }): Promise<User> {
  const { data } = await api.post<User>('/auth/register', payload);
  return data;
}
