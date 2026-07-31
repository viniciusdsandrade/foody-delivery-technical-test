import axios from 'axios';

// O backend emite mensagens em inglês; a UI é 100% pt-BR, então traduzimos
// por status HTTP em vez de repassar `data.message` cru para o usuário.
const STATUS_MESSAGES: Record<number, string> = {
  400: 'Dados inválidos. Revise os campos e tente novamente.',
  401: 'Sessão inválida ou expirada.',
  403: 'Você não tem permissão para esta ação.',
  404: 'Recurso não encontrado.',
  409: 'Operação em conflito com o estado atual. Tente novamente.',
  422: 'Operação não permitida neste estado.',
  500: 'Erro inesperado no servidor. Tente novamente mais tarde.',
};

/** Resolve a mensagem pt-BR para um erro da API (overrides por status > mapa > fallback). */
export function errorMessage(
  error: unknown,
  fallback: string,
  overrides: Record<number, string> = {},
): string {
  if (axios.isAxiosError(error) && error.response) {
    const status = error.response.status;
    return overrides[status] ?? STATUS_MESSAGES[status] ?? fallback;
  }
  return fallback;
}
