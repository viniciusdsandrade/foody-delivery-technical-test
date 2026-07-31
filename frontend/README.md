# Foody Tracker — front-end

SPA em React 19 + TypeScript (Vite) com Tailwind CSS. Consome a API do
`backend/` deste repositório; instruções completas de execução no
[README raiz](../README.md).

## Rotas

| Rota          | Acesso        | Tela |
|---------------|---------------|------|
| `/login`      | público       | Login (avisa quando a sessão expirou) |
| `/register`   | público       | Cadastro de cliente |
| `/orders`     | autenticado   | Lista de pedidos com filtro por status |
| `/orders/new` | CLIENT        | Criação de pedido com itens dinâmicos |
| `/orders/:id` | dono ou ADMIN | Detalhe com timeline; ADMIN vê os botões de transição válidos |

## Configuração

- `VITE_API_URL` — URL da API que o **navegador** chama (default
  `http://localhost:8080`). No Docker é build arg (Vite inline em build time).

## Scripts

```bash
npm run dev      # dev server em http://localhost:5173
npm run build    # tsc -b + vite build (saída em dist/)
npm run lint     # oxlint
```
