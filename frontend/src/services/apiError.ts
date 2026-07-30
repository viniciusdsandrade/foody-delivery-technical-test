import axios from 'axios';
import type { ApiError } from '../types';

/** Extracts the API error-contract message, falling back to a generic one. */
export function errorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error) && error.response?.data) {
    const data = error.response.data as ApiError;
    if (data.message) {
      return data.message;
    }
  }
  return fallback;
}
