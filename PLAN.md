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

## Versões pinadas

Compatibilidade verificada em 30/07/2026; qualquer upgrade fora desta tabela vira task própria.

| Artefato | Versão | Observação |
|----------|--------|------------|
| Java | 25 (LTS) | `<maven.compiler.release>25</maven.compiler.release>` |
| Spring Boot | 4.0.7 | último patch da linha 4.0.x (10/06/2026); não usar 4.1.x |
| jjwt | 0.13.0 | modular: `jjwt-api` (compile), `jjwt-impl` + `jjwt-jackson` (runtime); o artefato monolítico `jjwt` é legado |
| springdoc-openapi-starter-webmvc-ui | 3.0.3 | linha 3.0.x é a compatível com Boot 4 |
| JaCoCo | 0.8.14 | primeira versão com suporte **oficial** a Java 25 (bytecode 69) |
| H2 | gerenciada pelo Boot | não pinar manualmente |
| Front-end | React 19, React Router 7, Tailwind 4 (`@tailwindcss/vite`), axios 1.x | versões correntes do npm no scaffold (Vite 7) |

## Modelo de dados

- **users**: `id`, `name`, `email` (unique), `password_hash` (BCrypt), `role` (`CLIENT`/`ADMIN`), `created_at`
- **orders**: `id`, `customer_name`, endereço embutido (`street`, `number`, `complement`, `district`, `city`, `state`, `zip_code`), `status`, `total`, `user_id` (FK), `created_at`, `updated_at`
- **order_items**: `id`, `order_id` (FK), `name`, `quantity`, `unit_price`
- **order_status_history**: `id`, `order_id` (FK), `from_status`, `to_status`, `changed_by` (FK users), `changed_at`

Regras: `total` calculado no servidor (Σ `quantity × unit_price`); `orders` criados sempre com
status `RECEBIDO`; histórico registra o evento de criação (`from_status = null → RECEBIDO`).
Dinheiro é `BigDecimal` com scale 2 (`DECIMAL(10,2)` no schema), serializado como número JSON;
`quantity ≥ 1`, `unit_price > 0`.

## Máquina de estados

| De ↓ \ Para →     | RECEBIDO | EM_PREPARO | SAIU_PARA_ENTREGA | ENTREGUE | CANCELADO |
|-------------------|:--------:|:----------:|:-----------------:|:--------:|:---------:|
| RECEBIDO          | —        | ✅         | ❌                | ❌       | ✅        |
| EM_PREPARO        | ❌       | —          | ✅                | ❌       | ✅        |
| SAIU_PARA_ENTREGA | ❌       | ❌         | —                 | ✅       | ❌        |
| ENTREGUE          | final    | final      | final             | —        | final     |
| CANCELADO         | final    | final      | final             | final    | —         |

Transição inválida → `422 Unprocessable Content` com mensagem descritiva no contrato de erro.
A diagonal (novo status igual ao atual) também é transição inválida → `422`.

## Matriz de autorização

| Endpoint | CLIENT | ADMIN |
|----------|--------|-------|
| `POST /auth/register`, `POST /auth/login` | público | público |
| `POST /orders` | ✅ | ❌ (403) |
| `GET /orders` | apenas os próprios (filtro `?status=`) | todos (filtro `?status=`) |
| `GET /orders/{id}` | apenas se dono (senão 404) | ✅ |
| `PATCH /orders/{id}/status` | ❌ (403) | ✅ |
| `GET /orders/{id}/history` | apenas se dono (senão 404) | ✅ |

Semântica de negação:
- **Role errado** (rota proibida para o papel inteiro) → `403` — não vaza nada.
- **Ownership** (CLIENT acessando pedido de outro) → `404` — não revela a existência do recurso
  (evita enumeração de IDs). Mesmo código para pedido inexistente.
- `401` (token ausente/inválido) e `403` são emitidos pelo filtro de segurança **antes** do MVC;
  para seguirem o contrato de erro JSON exigem `AuthenticationEntryPoint` e `AccessDeniedHandler`
  customizados (T03) — `@RestControllerAdvice` sozinho não os captura.

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

## Contratos de API

Shapes de request/response por endpoint (fonte de verdade para back e front).

### `POST /auth/register` — público → `201`

```json
// request
{ "name": "Ana Souza", "email": "ana@example.com", "password": "secret1" }
// response 201
{ "id": 1, "name": "Ana Souza", "email": "ana@example.com", "role": "CLIENT" }
```

`name`/`email`/`password` obrigatórios; `password` ≥ 6 chars; e-mail duplicado → `409`.
Registro público cria sempre `CLIENT` (ADMIN só via seed).

### `POST /auth/login` — público → `200`

```json
// request
{ "email": "ana@example.com", "password": "secret1" }
// response 200
{
  "token": "<jwt>",
  "expiresIn": 7200,
  "user": { "id": 1, "name": "Ana Souza", "email": "ana@example.com", "role": "CLIENT" }
}
```

Credenciais inválidas → `401` (mesma mensagem para e-mail inexistente e senha errada).
JWT HS256, claims: `sub` = e-mail, `uid`, `role`, `iat`, `exp` (+2h). Secret ≥ 32 bytes.

### `POST /orders` — CLIENT → `201`

```json
// request
{
  "customerName": "Ana Souza",
  "address": {
    "street": "Rua das Flores", "number": "123", "complement": "ap 12",
    "district": "Centro", "city": "São Paulo", "state": "SP", "zipCode": "01001-000"
  },
  "items": [
    { "name": "Pizza Margherita", "quantity": 1, "unitPrice": 49.90 },
    { "name": "Refrigerante", "quantity": 2, "unitPrice": 4.95 }
  ]
}
// response 201 (shape completo de pedido — reutilizado nos GETs)
{
  "id": 1, "customerName": "Ana Souza", "status": "RECEBIDO", "total": 59.80,
  "address": { "street": "Rua das Flores", "number": "123", "complement": "ap 12",
               "district": "Centro", "city": "São Paulo", "state": "SP", "zipCode": "01001-000" },
  "items": [
    { "id": 1, "name": "Pizza Margherita", "quantity": 1, "unitPrice": 49.90 },
    { "id": 2, "name": "Refrigerante", "quantity": 2, "unitPrice": 4.95 }
  ],
  "createdAt": "2026-07-30T12:00:00Z", "updatedAt": "2026-07-30T12:00:00Z"
}
```

Validações: `items` ≥ 1; `quantity` ≥ 1; `unitPrice` > 0; endereço obrigatório
(`complement` opcional); `total` ignorado se enviado (sempre calculado no servidor).

### `GET /orders?status=` — autenticado → `200`

Lista (sem paginação — fora do escopo do teste) do shape completo acima, ordenada por
`createdAt` desc. `?status=` opcional para ambos os papéis; valor inválido → `400`.

### `GET /orders/{id}` — dono ou ADMIN → `200`

Shape completo. Inexistente ou de outro usuário (CLIENT) → `404`.

### `PATCH /orders/{id}/status` — ADMIN → `200`

```json
// request
{ "status": "EM_PREPARO" }
// response 200: shape completo do pedido atualizado
```

Transição inválida (incl. diagonal e estados finais) → `422`; status fora do enum → `400`.

### `GET /orders/{id}/history` — dono ou ADMIN → `200`

```json
[
  { "fromStatus": null, "toStatus": "RECEBIDO",
    "changedBy": { "id": 1, "name": "Ana Souza" }, "changedAt": "2026-07-30T12:00:00Z" },
  { "fromStatus": "RECEBIDO", "toStatus": "EM_PREPARO",
    "changedBy": { "id": 2, "name": "Admin" }, "changedAt": "2026-07-30T12:10:00Z" }
]
```

Ordenado por `changedAt` asc (linha do tempo).

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
- **Escopo**: `pom.xml` seguindo a tabela "Versões pinadas" (parent `spring-boot-starter-parent`
  4.0.7; starters web, security, data-jpa, validation, actuator; h2 runtime; `jjwt-api` 0.13.0 +
  `jjwt-impl`/`jjwt-jackson` runtime; `springdoc-openapi-starter-webmvc-ui` 3.0.3;
  `jacoco-maven-plugin` 0.8.14; `maven.compiler.release` 25), Maven wrapper,
  `application.yaml` (H2 arquivo `./data/foodydb`, H2 console, porta 8080,
  `jwt.secret`/`jwt.expiration-seconds` via env com default dev — secret default com ≥ 32 bytes,
  tratado como bytes UTF-8 e não Base64), dependência `spring-boot-h2console` (no Boot 4 o
  console H2 saiu do autoconfigure para módulo próprio), `.gitignore` Java/Maven
  (incluindo `data/`).
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
  (BCrypt, e-mail duplicado → 409), `JwtService` (emissão/validação conforme claims do
  "Contratos de API", expiração 2h), `JwtAuthenticationFilter`, `UserDetailsServiceImpl`,
  `SecurityConfig` (stateless; `/auth/**`, H2 console via `PathRequest.toH2Console()`,
  `/swagger-ui/**`, `/v3/api-docs/**` e `/actuator/health` públicos; CSRF off;
  `frameOptions.sameOrigin` para o H2 console; CORS para `localhost:5173`/`3000`),
  `AuthenticationEntryPoint` e `AccessDeniedHandler` customizados emitindo 401/403 no
  contrato de erro JSON.
- **Validação**: testes de unidade do `JwtService` e `AuthService`; `@WebMvcTest` dos endpoints;
  acesso a rota protegida sem token → 401.
- **Commits**: `feat: add JWT authentication with register and login`

### T04 — Criação e consulta de pedidos

- **Objetivo**: `POST /orders`, `GET /orders` (com `?status=`), `GET /orders/{id}`.
- **Escopo**: `OrderController`, `OrderService`, DTOs com Bean Validation conforme
  "Contratos de API" (≥1 item, `quantity ≥ 1`, `unitPrice > 0`, endereço obrigatório),
  `total` em `BigDecimal` calculado no servidor, regras de ownership (CLIENT só os próprios;
  pedido de outro usuário ou inexistente → 404; ADMIN em `POST /orders` → 403), registro do
  evento inicial no histórico.
- **Validação**: testes de unidade do `OrderService` e `@WebMvcTest` (happy path + 400 + 403 + 404).
- **Commits**: `feat: add order creation and listing endpoints`

### T05 — Máquina de estados e atualização de status

- **Objetivo**: `PATCH /orders/{id}/status` exclusivo de ADMIN, com transições validadas.
- **Escopo**: `OrderStateMachine` (mapa de transições da tabela acima), uso no `OrderService`,
  gravação em `order_status_history` com `changed_by`, transição inválida → 422.
- **Validação**: testes cobrindo a matriz completa 5×5 (válidas, inválidas e diagonal → 422),
  estados finais, 403 para CLIENT, status fora do enum → 400.
- **Commits**: `feat: add order status state machine with validated transitions`

### T06 — Histórico de status

- **Objetivo**: `GET /orders/{id}/history` (dono ou ADMIN), ordenado por `changed_at`.
- **Validação**: testes de service + controller, incluindo 404 para não-dono (conforme matriz de autorização).
- **Commits**: `feat: add order status history endpoint`

### T07 — Tratamento global de erros

- **Objetivo**: `@RestControllerAdvice` emitindo o contrato de erro para 400 (com
  `fieldErrors`), 401, 403, 404, 409 e 422.
- **Validação**: testes de controller verificando payload e status de cada cenário.
- **Commits**: `feat: add global exception handling with error contract`

### T08 — Seed e documentação OpenAPI

- **Objetivo**: `CommandLineRunner` **idempotente** (só insere se `users` vazio — H2 em
  arquivo persiste entre execuções) com `admin@foody.com/admin123` (ADMIN),
  `client@foody.com/client123` (CLIENT) e 3 pedidos em estados distintos, senhas via
  `PasswordEncoder`; SpringDoc em `/swagger-ui.html`.
- **Validação**: banco populado ao subir; reinício não duplica dados; Swagger lista os endpoints.
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
  front-end (build Node → nginx) + `docker-compose.yml` com as duas imagens, rede interna,
  `JWT_SECRET` via env no back-end, **volume** para `./data` (H2 em arquivo sobrevive ao
  container), `VITE_API_URL` como **build arg** do front (Vite injeta em build-time, não em
  runtime), healthcheck da API em `/actuator/health` e `depends_on: condition: service_healthy`.
- **Validação**: `docker compose up --build` sobe front (`:3000`) e API (`:8080`) funcionais;
  dados sobrevivem a `docker compose restart`.
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

- **Java 25 + Spring Boot 4**: mitigado — versões da tabela "Versões pinadas" verificadas em
  30/07/2026 (Boot 4.0.7, jjwt 0.13.0, springdoc 3.0.3, JaCoCo 0.8.14). Se ainda assim algo
  falhar no build, registrar a troca de versão no corpo do commit.
- **Cobertura 100%**: se algum ponto for artificial de testar (ex.: `main()`), documentar a
  exclusão no `jacoco:check` em vez de escrever teste sem valor.
- **Escopo**: nada além do plano (sem WebSocket, sem notificações). Melhorias viram task nova.

## Rollback

Repositório novo, histórico linear na `main`. Qualquer task problemática se resolve com
`git revert` do commit da task; a Fase 0 (este plano) permanece como baseline estável.
