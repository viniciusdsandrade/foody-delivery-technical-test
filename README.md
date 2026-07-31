# Foody — Mini Rastreador de Pedidos

Sistema simplificado de rastreamento de pedidos de delivery, desenvolvido como desafio técnico.
Permite cadastro/login de usuários, criação de pedidos com itens e endereço de entrega, e
acompanhamento do ciclo de vida do pedido (`RECEBIDO` → `EM_PREPARO` → `SAIU_PARA_ENTREGA` →
`ENTREGUE`, com opção de `CANCELADO`).

> **Status do projeto**: implementação concluída — back-end, front-end e Docker entregues
> conforme o [PLAN.md](PLAN.md) (tasks T01–T16, um commit por task). Back-end com 111 testes
> passando e `jacoco:check` exigindo 100% de cobertura de linhas nos pacotes de negócio.

## Stack

| Camada    | Tecnologias |
|-----------|-------------|
| Back-end  | Java 25, Spring Boot 4 (Web, Security, Data JPA, Validation), Maven, H2 (arquivo), JWT (jjwt), JaCoCo, SpringDoc OpenAPI |
| Front-end | React 19 + TypeScript (Vite), React Router, axios, Tailwind CSS |
| Infra     | Docker + docker-compose |

## Estrutura do repositório

```
.
├── backend/          # API REST (Spring Boot 4 + Maven)
├── frontend/         # Aplicação React (Vite + TS)
├── docker-compose.yml
├── PLAN.md           # Plano de implementação segmentado por tasks
└── README.md
```

## Pré-requisitos

- Java 25
- Node.js 22.12+ (exigência do Vite 8)
- Docker e docker-compose (opcional, para execução containerizada)

## Como executar

### Back-end

```bash
cd backend
./mvnw spring-boot:run
```

API em `http://localhost:8080`. Console do H2 em `http://localhost:8080/h2-console` —
desabilitado por padrão; para desenvolvimento local, suba com `H2_CONSOLE_ENABLED=true`
(JDBC URL: `jdbc:h2:file:./data/foodydb`). Documentação OpenAPI em
`http://localhost:8080/swagger-ui.html`. Para resetar o banco: `rm -rf backend/data`.

O primeiro start popula o banco (seed idempotente): os dois usuários da tabela abaixo e
3 pedidos de exemplo em estados distintos (`RECEBIDO`, `EM_PREPARO`, `ENTREGUE`), já com
histórico de transições. Reinícios não duplicam dados.

### Front-end

```bash
cd frontend
npm install
npm run dev
```

Aplicação em `http://localhost:5173`. A URL da API vem de `VITE_API_URL`
(default `http://localhost:8080`). Build de produção: `npm run build`.

### Docker (tudo de uma vez)

```bash
JWT_SECRET="troque-por-uma-chave-secreta-com-32-ou-mais-caracteres" docker compose up --build
```

Front-end em `http://localhost:3000`, API em `http://localhost:8080`. `JWT_SECRET` é
**obrigatória** — o compose falha rápido sem ela, porque o default de desenvolvimento está
versionado neste repositório público e não deve assinar tokens de uma stack exposta. O front
só sobe após a API ficar saudável (`/actuator/health`). O banco H2 fica no volume `foody-data`
e sobrevive a `docker compose restart`/`down` (para zerar: `docker compose down -v`).
`VITE_API_URL` (build arg do front) é a URL que o **navegador** chama, default
`http://localhost:8080`. `CORS_ALLOWED_ORIGINS` (env do back-end) lista as origens
permitidas, default `http://localhost:5173,http://localhost:3000` — ajuste se publicar o
front em outra origem.

> **Atualizando de uma versão anterior**: o back-end agora roda como usuário não-root
> (uid 10001). Volumes criados por versões antigas pertencem a root e impedem o H2 de
> escrever — rode `docker compose down -v` uma vez antes de subir a versão nova.

## Credenciais de exemplo (seed)

| Papel  | E-mail           | Senha     |
|--------|------------------|-----------|
| ADMIN  | admin@foody.com  | admin123  |
| CLIENT | client@foody.com | client123 |

## Autenticação

JWT stateless (HS256, expiração de 2h). O login retorna um token Bearer que o front-end envia
no header `Authorization` a cada requisição. Rotas públicas: `/auth/**`, Swagger UI,
`GET /actuator/health` e o console H2 quando habilitado; todo o restante exige autenticação. `401` e `403` seguem o mesmo
contrato de erro JSON da API.

## Endpoints principais

| Método | Rota                      | Acesso        | Descrição |
|--------|---------------------------|---------------|-----------|
| POST   | `/auth/register`          | público       | Cadastro (nome, e-mail, senha) |
| POST   | `/auth/login`             | público       | Login, retorna token JWT |
| POST   | `/orders`                 | CLIENT        | Cria pedido (cliente, itens, endereço) |
| GET    | `/orders?status=`         | autenticado   | CLIENT vê os próprios; ADMIN vê todos (filtro para ambos) |
| GET    | `/orders/{id}`            | dono ou ADMIN | Busca pedido por ID |
| PATCH  | `/orders/{id}/status`     | ADMIN         | Atualiza status (transições validadas) |
| GET    | `/orders/{id}/history`    | dono ou ADMIN | Histórico de transições do pedido |

Rota proibida para o papel → `403`; pedido de outro usuário ou inexistente → `404`
(não revela existência); atualização concorrente do mesmo pedido → `409` (lock otimista);
erro inesperado → `500` no mesmo contrato JSON. Contratos completos de request/response
no [PLAN.md](PLAN.md).

## Máquina de estados do pedido

| De ↓ \ Para →     | RECEBIDO | EM_PREPARO | SAIU_PARA_ENTREGA | ENTREGUE | CANCELADO |
|-------------------|:--------:|:----------:|:-----------------:|:--------:|:---------:|
| RECEBIDO          | —        | ✅         | ❌                | ❌       | ✅        |
| EM_PREPARO        | ❌       | —          | ✅                | ❌       | ✅        |
| SAIU_PARA_ENTREGA | ❌       | ❌         | —                 | ✅       | ❌        |
| ENTREGUE          | estado final (sem transições) |||||
| CANCELADO         | estado final (sem transições) |||||

Transição inválida (incluindo mesma origem/destino) retorna `422` com mensagem descritiva.
Toda transição é registrada no histórico do pedido (rastreabilidade com autor e timestamp).

## Decisões técnicas

- **JWT stateless** (Spring Security + jjwt): sem sessão no servidor, escalável e simples de
  consumir no React.
- **H2 em arquivo**: persistência real sem infra extra ("SQLite ou similar" no enunciado).
- **Modelagem relacional normalizada**: `users`, `orders`, `order_items` (1-N) e
  `order_status_history` (trilha de auditoria das transições).
- **Máquina de estados explícita** com validação de transições: `ENTREGUE` e `CANCELADO` são finais.
- **Roles**: `CLIENT` cria e acompanha os próprios pedidos; `ADMIN` visualiza todos e atualiza status.
- **Total calculado no servidor** (`BigDecimal`, scale 2): o front nunca define preço final.
- **Commits**: Conventional Commits, em inglês, pequenos e frequentes (um por task do plano).

## Testes

```bash
cd backend
./mvnw verify                # suíte completa + jacoco:check (relatório em target/site/jacoco)
```

111 testes (unidade de services e máquina de estados, slices `@WebMvcTest`/`@DataJpaTest` e
integração end-to-end com contexto real). O `jacoco:check` **falha o build** se a cobertura de
linhas de `service`, `controller` e `security` (incluindo a máquina de estados) cair abaixo de
100%. `dto`, `exception` e `config` também estão em 100%; entidades e `Application` ficam fora
da regra (exclusões justificadas no PLAN.md). Front-end sem testes, conforme escopo do plano.

## Checklist de entrega

- [x] Endpoints do escopo implementados e documentados (tabela acima + Swagger UI)
- [x] Roles `CLIENT`/`ADMIN` com matriz de autorização (403 por papel, 404 por ownership)
- [x] Máquina de estados 5×5 validada (matriz completa coberta por testes parametrizados)
- [x] Histórico de transições por pedido, com autor e timestamp
- [x] Seed idempotente (2 usuários + 3 pedidos) verificado em restarts
- [x] `docker-compose.yml` com os dois serviços, volume do banco e healthcheck
- [x] Repositório público, histórico linear na `main` com Conventional Commits

## Licença

Veja [LICENSE](LICENSE).
