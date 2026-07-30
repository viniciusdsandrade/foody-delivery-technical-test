# Foody — Mini Rastreador de Pedidos

Sistema simplificado de rastreamento de pedidos de delivery, desenvolvido como desafio técnico.
Permite cadastro/login de usuários, criação de pedidos com itens e endereço de entrega, e
acompanhamento do ciclo de vida do pedido (`RECEBIDO` → `EM_PREPARO` → `SAIU_PARA_ENTREGA` →
`ENTREGUE`, com opção de `CANCELADO`).

> **Status do projeto**: planejamento concluído. A implementação segue o [PLAN.md](PLAN.md),
> que detalha as tasks, critérios de aceite e commits esperados.

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
- Node.js 22+
- Docker e docker-compose (opcional, para execução containerizada)

## Como executar

### Back-end

```bash
cd backend
./mvnw spring-boot:run
```

API em `http://localhost:8080`. Console do H2 em `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:file:./data/foodydb`). Documentação OpenAPI em
`http://localhost:8080/swagger-ui.html`.

### Front-end

```bash
cd frontend
npm install
npm run dev
```

Aplicação em `http://localhost:5173`.

### Docker (tudo de uma vez)

```bash
docker compose up --build
```

Front-end em `http://localhost:3000`, API em `http://localhost:8080`.

## Credenciais de exemplo (seed)

| Papel  | E-mail           | Senha     |
|--------|------------------|-----------|
| ADMIN  | admin@foody.com  | admin123  |
| CLIENT | client@foody.com | client123 |

## Autenticação

JWT stateless. O login retorna um token Bearer que o front-end envia no header
`Authorization` a cada requisição. Apenas `/auth/**` é público; todo o restante exige autenticação.

## Endpoints principais

| Método | Rota                      | Acesso        | Descrição |
|--------|---------------------------|---------------|-----------|
| POST   | `/auth/register`          | público       | Cadastro (nome, e-mail, senha) |
| POST   | `/auth/login`             | público       | Login, retorna token JWT |
| POST   | `/orders`                 | CLIENT        | Cria pedido (cliente, itens, endereço) |
| GET    | `/orders?status=`         | autenticado   | CLIENT vê os próprios; ADMIN vê todos |
| GET    | `/orders/{id}`            | dono ou ADMIN | Busca pedido por ID |
| PATCH  | `/orders/{id}/status`     | ADMIN         | Atualiza status (transições validadas) |
| GET    | `/orders/{id}/history`    | dono ou ADMIN | Histórico de transições do pedido |

## Máquina de estados do pedido

| De ↓ \ Para →     | RECEBIDO | EM_PREPARO | SAIU_PARA_ENTREGA | ENTREGUE | CANCELADO |
|-------------------|:--------:|:----------:|:-----------------:|:--------:|:---------:|
| RECEBIDO          | —        | ✅         | ❌                | ❌       | ✅        |
| EM_PREPARO        | ❌       | —          | ✅                | ❌       | ✅        |
| SAIU_PARA_ENTREGA | ❌       | ❌         | —                 | ✅       | ❌        |
| ENTREGUE          | estado final (sem transições) |||||
| CANCELADO         | estado final (sem transições) |||||

Transição inválida retorna `422` com mensagem descritiva. Toda transição é registrada no
histórico do pedido (rastreabilidade).

## Decisões técnicas

- **JWT stateless** (Spring Security + jjwt): sem sessão no servidor, escalável e simples de
  consumir no React.
- **H2 em arquivo**: persistência real sem infra extra ("SQLite ou similar" no enunciado).
- **Modelagem relacional normalizada**: `users`, `orders`, `order_items` (1-N) e
  `order_status_history` (trilha de auditoria das transições).
- **Máquina de estados explícita** com validação de transições: `ENTREGUE` e `CANCELADO` são finais.
- **Roles**: `CLIENT` cria e acompanha os próprios pedidos; `ADMIN` visualiza todos e atualiza status.
- **Commits**: Conventional Commits, em inglês, pequenos e frequentes.

## Testes

```bash
cd backend
./mvnw test                  # suíte completa
./mvnw jacoco:report         # relatório em target/site/jacoco
```

Meta: 100% de cobertura de linhas nos pacotes de negócio (`service`, `controller`, `security`,
máquina de estados), com exclusões justificadas (DTOs, entidades, configuração). Detalhes no PLAN.md.

## Licença

Veja [LICENSE](LICENSE).
