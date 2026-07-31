import axios from 'axios';

export const TOKEN_STORAGE_KEY = 'foody.token';
export const USER_STORAGE_KEY = 'foody.user';
export const SESSION_EXPIRED_KEY = 'foody.sessionExpired';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    // A 401 while holding a token means the session expired or is invalid:
    // drop the stored credentials and go back to the login page. A 401 from
    // the login call itself carries no token and is handled by the form.
    if (axios.isAxiosError(error) && error.response?.status === 401
        && localStorage.getItem(TOKEN_STORAGE_KEY)) {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
      sessionStorage.setItem(SESSION_EXPIRED_KEY, '1');
      window.location.assign('/login');
    }
    return Promise.reject(error);
  },
);

export default api;
