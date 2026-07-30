# PLAN — Foody Mini Rastreador de Pedidos

Plano de implementação do desafio técnico, segmentado por tasks executáveis.
Cada task deixa o repositório em estado buildável e gera um ou mais commits pequenos
(Conventional Commits, em inglês).

## Decisões consolidadas (respostas do autor)

| # | Tema | Decisão |
|---|------|---------|
| 1 | Stack back-end | Java 25 + Spring Boot 4 + Maven |
| 2 | Autenticação | JWT stateless com Spring Security (jjwt) |
| 3 | Banco | H2 em arquivo (`jdbc:h2:file:./data/foodydb`) |
| 4 | Modelagem | Relacional normalizada: `users`, `orders`, `order_items` (1-N), `order_status_history` |
| 5 | Status | Máquina de estados com validação de transições |
| 6 | Escopo de pedidos | CLIENT vê apenas os próprios; ADMIN vê todos |
| 7 | Roles | `CLIENT` e `ADMIN` |
| 8 | Front-end | Vite + React 19 + TypeScript |
| 9 | Estilização | Tailwind CSS |
| 10 | Dados no front | axios + React Router + Context API (estado local) |
| 11 | Status no front | Botões de transição para ADMIN (bônus) |
| 12 | Estrutura | Monorepo: `backend/` + `frontend/` |
| 13 | Docker | `docker-compose.yml` com os dois serviços |
| 14 | Testes | Back-end: unit (service) + controller; meta 100% de cobertura nos pacotes de negócio. Front: sem testes |
| 15 | Idioma | Código e commits em inglês; README em português |
| 16 | Commits | Conventional Commits, pequenos e frequentes |

## Modelo de dados

- **users**: `id`, `name`, `email` (unique), `password_hash` (BCrypt), `role` (`CLIENT`/`ADMIN`), `created_at`
- **orders**: `id`, `customer_name`, endereço embutido (`street`, `number`, `complement`, `district`, `city`, `state`, `zip_code`), `status`, `total`, `user_id` (FK), `created_at`, `updated_at`
- **order_items**: `id`, `order_id` (FK), `name`, `quantity`, `unit_price`
- **order_status_history**: `id`, `order_id` (FK), `from_status`, `to_status`, `changed_by` (FK users), `changed_at`

Regras: `total` calculado no servidor (Σ `quantity × unit_price`); `orders` criados sempre com
status `RECEBIDO`; histórico registra o evento de criação (`from_status = null → RECEBIDO`).

## Máquina de estados

| De ↓ \ Para →     | RECEBIDO | EM_PREPARO | SAIU_PARA_ENTREGA | ENTREGUE | CANCELADO |
|-------------------|:--------:|:----------:|:-----------------:|:--------:|:---------:|
| RECEBIDO          | —        | ✅         | ❌                | ❌       | ✅        |
| EM_PREPARO        | ❌       | —          | ✅                | ❌       | ✅        |
| SAIU_PARA_ENTREGA | ❌       | ❌         | —                 | ✅       | ❌        |
| ENTREGUE          | final    | final      | final             | —        | final     |
| CANCELADO         | final    | final      | final             | final    | —         |

Transição inválida → `422 Unprocessable Content` com mensagem descritiva no contrato de erro.

## Matriz de autorização

| Endpoint | CLIENT | ADMIN |
|----------|--------|-------|
| `POST /auth/register`, `POST /auth/login` | público | público |
| `POST /orders` | ✅ | ❌ (403) |
| `GET /orders` | apenas os próprios | todos (filtro `?status=`) |
| `GET /orders/{id}` | apenas se dono | ✅ |
| `PATCH /orders/{id}/status` | ❌ (403) | ✅ |
| `GET /orders/{id}/history` | apenas se dono | ✅ |

## Contrato de erro

```json
{
  "timestamp": "2026-07-30T12:00:00Z",
  "status": 422,
  "error": "Unprocessable Content",
  "message": "Cannot transition from ENTREGUE to CANCELADO",
  "path": "/orders/1/status",
  "fieldErrors": [{ "field": "email", "message": "must not be blank" }]
}
```

`fieldErrors` presente apenas em erros de validação (400).

---

## Fase 0 — Fundação ✅

### T00 — Planejamento (esta entrega)

- **Arquivos**: `README.md`, `PLAN.md`
- **Commits**: `docs: add README with project overview and run instructions`,
  `docs: add implementation plan segmented by tasks`
- **Critério de aceite**: arquivos na `main` remota; nenhum código de implementação commitado.

---

## Fase 1 — Back-end (`backend/`)

Pacote base: `com.foody.tracker` (`controller`, `service`, `repository`, `entity`, `dto`,
`security`, `config`, `exception`).

### T01 — Scaffold do projeto

- **Objetivo**: projeto Maven funcional com Java 25 e Spring Boot 4.
- **Escopo**: `pom.xml` (web, security, data-jpa, validation, h2, jjwt 0.12.x, springdoc,
  actuator, jacoco), Maven wrapper, `application.yaml` (H2 arquivo, H2 console, porta 8080,
  `jwt.secret`/`jwt.expiration` via env com default dev), `.gitignore` Java/Maven.
- **Validação**: `./mvnw spring-boot:run` sobe; `GET /actuator/health` retorna `UP`.
- **Commits**: `chore: scaffold Spring Boot backend with Maven`

### T02 — Modelo de domínio e persistência

- **Objetivo**: entidades JPA e repositórios conforme "Modelo de dados".
- **Escopo**: `User`, `Order`, `OrderItem`, `OrderStatusHistory`, enums `Role` e `OrderStatus`,
  repositórios Spring Data (`findByEmail`, `findByUserIdOrderByCreatedAtDesc`, etc.).
- **Validação**: schema gerado no H2 arquivo; `@DataJpaTest` cobrindo as queries customizadas.
- **Commits**: `feat: add domain entities and repositories`

### T03 — Autenticação JWT

- **Objetivo**: registro, login e filtro JWT stateless.
- **Escopo**: `AuthController` (`POST /auth/register`, `POST /auth/login`), `AuthService`
  (BCrypt, e-mail duplicado → 409), `JwtService` (emissão/validação, expiração 2h),
  `JwtAuthenticationFilter`, `UserDetailsServiceImpl`, `SecurityConfig` (stateless;
  `/auth/**`, `/h2-console/**`, `/swagger-ui/**` públicos; CORS para `localhost:5173`/`3000`).
- **Validação**: testes de unidade do `JwtService` e `AuthService`; `@WebMvcTest` dos endpoints;
  acesso a rota protegida sem token → 401.
- **Commits**: `feat: add JWT authentication with register and login`

### T04 — Criação e consulta de pedidos

- **Objetivo**: `POST /orders`, `GET /orders` (com `?status=`), `GET /orders/{id}`.
- **Escopo**: `OrderController`, `OrderService`, DTOs com Bean Validation (≥1 item,
  `quantity > 0`, endereço obrigatório), cálculo de `total` no servidor, regras de ownership
  (CLIENT só os próprios; dono ou ADMIN no `{id}` → 403/404 conforme o caso), registro do
  evento inicial no histórico.
- **Validação**: testes de unidade do `OrderService` e `@WebMvcTest` (happy path + 400 + 403/404).
- **Commits**: `feat: add order creation and listing endpoints`

### T05 — Máquina de estados e atualização de status

- **Objetivo**: `PATCH /orders/{id}/status` exclusivo de ADMIN, com transições validadas.
- **Escopo**: `OrderStateMachine` (mapa de transições da tabela acima), uso no `OrderService`,
  gravação em `order_status_history` com `changed_by`, transição inválida → 422.
- **Validação**: testes cobrindo a matriz completa 5×5 (válidas e inválidas), estados finais,
  403 para CLIENT.
- **Commits**: `feat: add order status state machine with validated transitions`

### T06 — Histórico de status

- **Objetivo**: `GET /orders/{id}/history` (dono ou ADMIN), ordenado por `changed_at`.
- **Validação**: testes de service + controller, incluindo 403 para não-dono.
- **Commits**: `feat: add order status history endpoint`

### T07 — Tratamento global de erros

- **Objetivo**: `@RestControllerAdvice` emitindo o contrato de erro para 400 (com
  `fieldErrors`), 401, 403, 404, 409 e 422.
- **Validação**: testes de controller verificando payload e status de cada cenário.
- **Commits**: `feat: add global exception handling with error contract`

### T08 — Seed e documentação OpenAPI

- **Objetivo**: `CommandLineRunner` com `admin@foody.com/admin123` (ADMIN),
  `client@foody.com/client123` (CLIENT) e 3 pedidos em estados distintos; SpringDoc em
  `/swagger-ui.html`.
- **Validação**: banco populado ao subir; Swagger lista os endpoints.
- **Commits**: `chore: add seed data and OpenAPI documentation`

### T09 — Cobertura de 100%

- **Objetivo**: JaCoCo com meta de 100% de linhas em `service`, `controller`, `security` e
  máquina de estados; exclusões justificadas (DTOs, entidades, `config`, `Application`).
- **Validação**: `./mvnw verify` com `jacoco:check` verde; relatório em `target/site/jacoco`.
- **Commits**: `test: reach full coverage on business packages`

---

## Fase 2 — Front-end (`frontend/`)

Estrutura: `src/{pages,components,services,context,hooks,types}`.

### T10 — Scaffold do front-end

- **Objetivo**: Vite + React 19 + TypeScript + Tailwind + React Router + axios.
- **Escopo**: instância axios (`baseURL` via env, interceptor que anexa o Bearer token e
  redireciona ao login em 401), rotas base, layout.
- **Commits**: `chore: scaffold React frontend with Vite and Tailwind`

### T11 — Fluxo de autenticação

- **Objetivo**: páginas Login e Register, `AuthContext` (token em `localStorage`), guards
  `PrivateRoute` e `AdminRoute`.
- **Validação**: login redireciona para `/orders`; rota privada sem token volta para `/login`.
- **Commits**: `feat: add login and registration flow`

### T12 — Listagem e criação de pedidos

- **Objetivo**: `/orders` (badge de status, filtro por status) e `/orders/new` (itens
  dinâmicos adicionar/remover, endereço, validações no form).
- **Commits**: `feat: add orders list and order creation pages`

### T13 — Detalhe do pedido com timeline e ações ADMIN

- **Objetivo**: `/orders/:id` com timeline do histórico de status e botões de transição
  (apenas os válidos, visíveis para ADMIN), incluindo cancelamento quando permitido.
- **Commits**: `feat: add order detail with status timeline and admin actions`

### T14 — Polimento de UX

- **Objetivo**: estados de loading/empty/error, formatação de data e moeda em pt-BR,
  feedback (toasts) em criar/atualizar/erro.
- **Commits**: `feat: polish UX with loading, empty and error states`

---

## Fase 3 — Entrega

### T15 — Docker

- **Objetivo**: Dockerfile multi-stage do back-end (build Maven → runtime JRE 25) e do
  front-end (build Node → nginx) + `docker-compose.yml` com as duas imagens, envs
  (`JWT_SECRET`, URL da API) e rede interna.
- **Validação**: `docker compose up --build` sobe front (`:3000`) e API (`:8080`) funcionais.
- **Commits**: `chore: add Dockerfiles and docker-compose`

### T16 — Revisão final e entrega

- **Objetivo**: README revisado contra o comportamento real, histórico de commits limpo,
  checklist de entrega (endpoints, roles, máquina de estados, seed, docker-compose).
- **Validação**: repositório público no GitHub com tudo na `main`.
- **Commits**: `docs: finalize README for delivery`

---

## Cronograma estimado (prazo: 03/08/2026 23:59)

| Fase | Tasks | Estimativa |
|------|-------|-----------|
| 0 — Fundação | T00 | concluída |
| 1 — Back-end | T01–T09 | 6–8 h |
| 2 — Front-end | T10–T14 | 4–5 h |
| 3 — Entrega | T15–T16 | 1–2 h |
| **Total** | | **11–15 h** |

## Riscos e mitigações

- **Java 25 + Spring Boot 4**: stack recente; se alguma dependência (jjwt, springdoc) tiver
  incompatibilidade, fixar a última versão estável compatível e registrar no commit.
- **Cobertura 100%**: se algum ponto for artificial de testar (ex.: `main()`), documentar a
  exclusão no `jacoco:check` em vez de escrever teste sem valor.
- **Escopo**: nada além do plano (sem WebSocket, sem notificações). Melhorias viram task nova.

## Rollback

Repositório novo, histórico linear na `main`. Qualquer task problemática se resolve com
`git revert` do commit da task; a Fase 0 (este plano) permanece como baseline estável.
