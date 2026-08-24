# Transaction Execution API

API REST para la ejecución y consulta de transacciones financieras (CREDIT / DEBIT) contra un proveedor externo que es la fuente de verdad de saldos y bloqueos.

> Challenge técnico backend — Spin.

---

## Tabla de contenido

1. [Resumen](#resumen)
2. [Quick start](#quick-start)
3. [Arquitectura](#arquitectura)
4. [Decisiones de diseño](#decisiones-de-diseño)
5. [API](#api)
6. [Reglas de negocio](#reglas-de-negocio)
7. [Máquina de estados](#máquina-de-estados)
8. [Idempotencia](#idempotencia)
9. [Resiliencia y reintentos](#resiliencia-y-reintentos)
10. [Proveedor externo (mock)](#proveedor-externo-mock)
11. [Seguridad](#seguridad)
12. [Estrategia de pruebas](#estrategia-de-pruebas)
13. [Consideraciones de alto volumen](#consideraciones-de-alto-volumen)
14. [Despliegue](#despliegue)
15. [CI/CD](#cicd)
16. [Uso de Inteligencia Artificial](#uso-de-inteligencia-artificial)
17. [Preguntas frecuentes sobre el diseño](#preguntas-frecuentes-sobre-el-diseño)
18. [Qué haría con más tiempo](#qué-haría-con-más-tiempo)

---

## Resumen

El servicio recibe solicitudes de transacción, las valida contra reglas de negocio locales, las ejecuta contra un proveedor externo, persiste el resultado y expone un endpoint de consulta con filtros y paginación.

**No gestiona balances.** El proveedor externo valida y administra saldos y bloqueos; este servicio ejecuta, registra y expone.

### Stack

| Componente | Tecnología | Rol |
|---|---|---|
| Lenguaje | Java 21 | Records, sealed interfaces, pattern matching |
| Framework | Spring Boot 3.5 | Web, Data JPA, Security, Kafka |
| Persistencia | PostgreSQL 16 + Flyway | Registro transaccional ACID |
| Cache | Redis 7 | Idempotencia de peticiones |
| Mensajería | Apache Kafka (KRaft) | Reintentos asíncronos + DLQ |
| Resiliencia | Resilience4j | Timeout, retry, circuit breaker |
| Mock proveedor | Mockoon | Simulación con estado |
| Documentación | springdoc-openapi | Swagger UI |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit | |
| Infraestructura | Docker, Kubernetes, ECS Fargate, Terraform | |
| CI/CD | GitHub Actions | |

### Alcance

El reto pedía dos endpoints, validaciones previas, manejo de ambos escenarios del proveedor y tests unitarios. Todo eso está implementado. Además se incluye —marcado como extra, no como requisito— seguridad con JWT, idempotencia, reintentos asíncronos, documentación OpenAPI, contenedorización, manifiestos de despliegue y pipeline de CI/CD.

Cada componente adicional responde a un problema concreto del enunciado; la sección [Decisiones de diseño](#decisiones-de-diseño) justifica cada uno. Lo que se consideró innecesario para el alcance se documenta explícitamente en [Qué haría con más tiempo](#qué-haría-con-más-tiempo) en lugar de implementarse a medias.

---

## Quick start

### Requisitos

- Docker y Docker Compose
- JDK 21 (sólo si se ejecuta la app fuera de Docker)

### Opción A — Todo en Docker

```bash
docker compose up -d --build
curl -s localhost:8080/actuator/health | jq
```

### Opción B — Infraestructura en Docker, app local

```bash
docker compose up -d postgres redis kafka mockoon
./mvnw spring-boot:run
```

| Recurso | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Mock del proveedor | http://localhost:3001 |

### Prueba de humo

```bash
# 1. Obtener token
TOKEN=$(curl -s -X POST localhost:8080/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo"}' | jq -r .accessToken)

# 2. Ejecutar una transacción
curl -s -X POST localhost:8080/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
        "accountId": "acc-123456",
        "type": "CREDIT",
        "amount": 1500.00,
        "currency": "MXN",
        "description": "Transferencia recibida"
      }' | jq

# 3. Consultar
curl -s -H "Authorization: Bearer $TOKEN" \
  'localhost:8080/transactions?accountId=acc-123456&page=0&limit=20' | jq
```

### Ejecutar pruebas

```bash
./mvnw test              # unitarias
./mvnw verify            # + integración (Testcontainers) + reporte JaCoCo
open target/site/jacoco/index.html
```

### Demostración completa de los cuatro caminos

```bash
# 1. EXECUTED (201)
curl -si -XPOST localhost:8080/transactions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN"}' | head -1

# 2. REJECTED por regla local, sin llamar al proveedor (422)
curl -s -XPOST localhost:8080/transactions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-1","type":"DEBIT","amount":0.50,"currency":"USD"}' | jq

# 3. REJECTED por el proveedor (422)
curl -s -XPOST localhost:8080/transactions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-insufficient","type":"DEBIT","amount":500,"currency":"MXN"}' | jq -r .failureCode

# 4. PENDING + reintento asíncrono (202)
docker compose stop mockoon
curl -s -XPOST localhost:8080/transactions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-retry","type":"CREDIT","amount":250,"currency":"MXN"}' | jq -r .status
docker compose start mockoon
sleep 40
curl -s -H "Authorization: Bearer $TOKEN" \
  'localhost:8080/transactions?accountId=acc-retry' | jq -r '.content[0].status'

# 5. Idempotencia: dos peticiones, un solo cargo
KEY=$(uuidgen)
for i in 1 2; do
  curl -s -XPOST localhost:8080/transactions -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' \
    -d '{"accountId":"acc-idem","type":"CREDIT","amount":100,"currency":"MXN"}' \
    | jq -r '"\(.id) \(.balanceAfter)"'
done
# Ambas líneas idénticas: el proveedor se llamó una sola vez.
```

---

## Arquitectura

Arquitectura Hexagonal (Ports & Adapters). La regla de dependencia es estricta y **verificada automáticamente por ArchUnit**: las dependencias apuntan siempre hacia adentro. Si alguien introduce un `import` de Spring en el dominio, el build falla.

```
                    ┌─────────────────────────────────┐
   HTTP  ──────────▶│  adapter.in.rest                │
                    │  adapter.in.messaging (Kafka)   │
   Kafka ──────────▶└────────────────┬────────────────┘
                                     │ puertos de entrada
                    ┌────────────────▼────────────────┐
                    │        application.service      │
                    │  ┌──────────────────────────┐   │
                    │  │        DOMAIN            │   │
                    │  │  modelo · reglas · estado│   │
                    │  │  (sin frameworks)        │   │
                    │  └──────────────────────────┘   │
                    └────────────────┬────────────────┘
                                     │ puertos de salida
        ┌────────────────┬───────────┴───────┬──────────────────┐
        ▼                ▼                   ▼                  ▼
   Postgres (JPA)   Redis (idempot.)   Provider (HTTP)   Kafka (producer)
```

### Qué vive en el dominio

Como el servicio no gestiona balances, el dominio es pequeño pero crítico:

1. **Reglas de negocio** — monto mínimo, límite de débito, moneda soportada.
2. **Máquina de estados** — transiciones validadas dentro del agregado.
3. **Interpretación del resultado del proveedor** — `ProviderResult` como `sealed interface`.

El dominio no tiene una sola importación de Spring, Jakarta, Jackson o Kafka.

### Adaptadores

| Tipo | Adaptador | Puerto |
|---|---|---|
| Entrada | `TransactionController` (REST) | `ExecuteTransactionUseCase`, `SearchTransactionsUseCase` |
| Entrada | `TransactionRetryListener` (Kafka) | `RetryTransactionUseCase` |
| Salida | `TransactionPersistenceAdapter` (JPA) | `TransactionRepositoryPort` |
| Salida | `PaymentProviderAdapter` (HTTP) | `PaymentProviderPort` |
| Salida | `RedisIdempotencyAdapter` | `IdempotencyPort` |
| Salida | `KafkaEventPublisherAdapter` | `TransactionEventPublisherPort` |

**El listener de Kafka es un adaptador de entrada, no de salida.** Consume del retry topic e invoca un caso de uso, igual que el controller REST. Kafka es simplemente otro *driver* que empuja el hexágono.

### Estructura de paquetes

```
mx.spin.transactions
├── domain/            # modelo, políticas, eventos, excepciones — sin frameworks
│   ├── model/         # Transaction (agregado), Money, Currency, ProviderResult
│   ├── policy/        # TransactionRule + implementaciones + motor
│   ├── event/         # TransactionRetryRequested
│   └── exception/     # jerarquía con código de error
├── application/
│   ├── common/        # PageResult
│   ├── port/in/       # casos de uso, commands, queries
│   ├── port/out/      # contratos hacia infraestructura
│   └── service/       # orquestación + decorador de idempotencia
├── adapter/
│   ├── in/rest/       # controllers, DTOs, exception handler
│   ├── in/messaging/  # listener Kafka con retry topics y DLT
│   └── out/           # persistence, provider, cache, messaging
└── config/            # wiring de Spring — el único lugar que conoce todas las capas
```

---

## Decisiones de diseño

### 1. Rechazo de negocio vs. fallo técnico

Es la decisión que ordena todo el diseño. El contrato del proveedor agrupa respuestas 4XX y 5XX en un mismo bloque, pero su semántica es opuesta:

| Escenario | Estado persistido | ¿Reintentar? | HTTP |
|---|---|---|---|
| Viola regla local | no se ejecuta ni persiste | No | `422` |
| Proveedor 4XX (`INSUFFICIENT_FUNDS`) | `REJECTED` | **Nunca** | `422` |
| Proveedor 5XX / timeout / circuito abierto | `PENDING` | **Sí, vía Kafka** | `202` |

Reintentar un `INSUFFICIENT_FUNDS` es ruido inútil: el resultado será idéntico. Reintentar un `504` es obligatorio: no sabemos si el cargo se aplicó. De esa incertidumbre nace la necesidad de idempotencia.

En código, la distinción se hace explícita con tipos:

```java
sealed interface ProviderResult { Approved, Rejected }   // resultados de NEGOCIO
class ProviderUnavailableException extends DomainException // fallo TÉCNICO
```

El `switch` exhaustivo sobre la interfaz sellada garantiza en tiempo de compilación que ningún caso quede sin tratar. Si mañana se añade un tercer resultado, el build falla hasta que se maneje.

### 2. PostgreSQL para persistencia

Transacciones financieras exigen garantías ACID no negociables. Decisiones concretas:

- `NUMERIC(19,4)` para montos — nunca `float`/`double`.
- `BigDecimal` en Java, comparado siempre con `compareTo` y nunca con `equals` (`1500.0` y `1500.00` son `equals`-distintos pero numéricamente iguales; es el bug clásico en dinero).
- Índice compuesto `(account_id, created_at DESC)` alineado al patrón de acceso dominante.
- Índice parcial sobre estados no terminales, para el barrido de reconciliación.
- Índice único parcial sobre `provider_transaction_id` como segunda línea de defensa contra doble ejecución.
- Flyway como dueño del esquema; `ddl-auto: validate` (nunca `update`).

### 3. Redis para idempotencia

Un índice único en Postgres también garantizaría unicidad, pero implicaría una escritura extra en el *hot path* de cada petición. A escala de millones de transacciones diarias, Redis aporta:

- Latencia sub-milisegundo.
- **TTL automático** — Postgres requeriría un job de purga.
- Desacoplamiento de ciclos de vida: el dato de idempotencia es efímero (24 h), el de negocio es permanente.

Redis es *best-effort*: si cae, la política es **fail-open** (se procesa sin la garantía, priorizando disponibilidad). Es una decisión consciente y registrada en el log, no un descuido. La alternativa —rechazar peticiones porque el cache está caído— sería peor para un sistema de pagos.

### 4. Kafka para reintentos

Sin Kafka, un `504` del proveedor deja la transacción en un limbo del que nadie se entera. Con Kafka:

- Reintentos con backoff exponencial sin bloquear al cliente.
- Si se agotan, la transacción cae en DLQ para intervención manual.
- **Cero transacciones perdidas.**

Particionado por `accountId` para garantizar orden por cuenta y evitar reintentos concurrentes sobre la misma.

El dominio nunca importa `org.apache.kafka`. El servicio de aplicación sólo conoce `TransactionEventPublisherPort.publishRetryRequested(evento)`, donde el evento es un `record` propio. Topics, serialización y headers viven en el adaptador.

### 5. Idempotencia como Decorator

`ExecuteTransactionService` **no contiene una sola línea sobre idempotencia**. La funcionalidad se compone por fuera:

```java
IdempotentExecuteTransactionDecorator implements ExecuteTransactionUseCase {
    private final ExecuteTransactionUseCase delegate;   // el servicio original
    ...
}
```

El `BeanConfig` marca el decorador como `@Primary` y el controller lo recibe sin enterarse. Cuando se añadió esta funcionalidad, el servicio de ejecución no se modificó.

Esto es el retorno concreto de haber definido los puertos antes de escribir adaptadores: dos funcionalidades grandes (idempotencia y reintentos asíncronos) se añadieron sin tocar el núcleo del sistema.

### 6. Sin `@Transactional` alrededor de la llamada al proveedor

`ExecuteTransactionService.execute()` deliberadamente **no** es transaccional. Mantener una transacción de base de datos abierta durante una llamada HTTP externa retiene una conexión del pool durante cientos de milisegundos; a este volumen, el pool se agota antes que cualquier otro recurso.

Las fronteras transaccionales viven en el adaptador de persistencia, por operación. El precio es que la transacción puede quedar `PENDING` si el proceso muere a mitad de la llamada — que es exactamente el estado correcto para ese escenario, y el que el flujo de reconciliación resolvería.

### 7. `java.time.Clock` en lugar de un puerto propio

`Clock` ya es un puerto: JDK puro, inyectable, y `Clock.fixed()` da tests deterministas. Envolverlo habría sido ceremonia sin retorno. Se prefirió el estándar sobre la simetría.

### 8. Agregado mutable, no `record`

`Transaction` tiene ciclo de vida y transiciones legales. Se modela como clase con métodos de negocio (`markExecuted`, `markRejected`, `registerRetryAttempt`) y cero setters. Un `EXECUTED` no puede volver a `PENDING`: lanza `InvalidStateTransitionException`.

### 9. Entidad JPA separada del agregado

`TransactionJpaEntity` y `Transaction` son clases distintas unidas por un mapper. Es duplicación aparente, pero evita que las restricciones de JPA (constructor sin argumentos, campos mutables, anotaciones) contaminen el modelo de dominio. El coste es un mapper; el beneficio es que el dominio no cambia si mañana se migra a MongoDB.

### 10. Acumulación de violaciones de reglas

El motor evalúa todas las reglas y reporta el conjunto completo, en lugar de fallar en la primera. Un cliente que envía `0.50 USD` recibe los dos errores en una sola respuesta y corrige de una vez.

---

## API

Documentación interactiva en `/swagger-ui.html`.

### `POST /transactions`

**Headers:** `Authorization: Bearer <jwt>`, `Idempotency-Key: <uuid>` (opcional pero recomendado)

```json
{
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Transferencia recibida"
}
```

**`201 Created`**

```json
{
  "id": "3f2a1b4c-...",
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Transferencia recibida",
  "status": "EXECUTED",
  "providerTransactionId": "txn-789",
  "balanceAfter": 5500.00,
  "createdAt": "2025-03-15T10:30:00Z"
}
```

**Códigos de respuesta**

| Código | Significado | Estado resultante |
|---|---|---|
| `201` | Ejecutada correctamente | `EXECUTED` |
| `202` | Aceptada; proveedor no disponible, encolada para reintento | `PENDING` |
| `400` | Request malformado (validación sintáctica) | — |
| `401` | Token ausente o inválido | — |
| `409` | Petición idéntica en curso (misma `Idempotency-Key`) | — |
| `422` | Violación de regla de negocio o rechazo del proveedor | `REJECTED` |

Los errores siguen **RFC 7807 (Problem Details)**, estándar nativo de Spring Boot 3:

```json
{
  "type": "https://spin.mx/errors/business-rule-violation",
  "title": "Business rule violation",
  "status": 422,
  "detail": "Transaction amount must be greater than 1.00",
  "instance": "/transactions",
  "violations": [
    { "code": "AMOUNT_BELOW_MINIMUM", "message": "Transaction amount must be greater than 1.00" }
  ]
}
```

### `GET /transactions`

| Query param | Tipo | Default |
|---|---|---|
| `accountId` | string | — |
| `status` | `PENDING` \| `EXECUTED` \| `REJECTED` \| `FAILED` | — |
| `type` | `CREDIT` \| `DEBIT` | — |
| `page` | int ≥ 0 | `0` |
| `limit` | int 1–100 | `20` |

```json
{
  "content": [ /* ... */ ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8
}
```

Los filtros se componen dinámicamente con JPA Specifications: sólo se añaden al `WHERE` los parámetros presentes. El orden por defecto es `createdAt DESC`, que coincide con el índice compuesto — no es casualidad, el índice se diseñó para esta consulta.

---

## Reglas de negocio

Se evalúan **antes** de llamar al proveedor. Si alguna falla, la transacción no se ejecuta ni se persiste.

| # | Regla | Detalle | Código de error |
|---|---|---|---|
| 1 | Monto mínimo | Estrictamente mayor a `$1.00` — `1.00` exacto se **rechaza** | `AMOUNT_BELOW_MINIMUM` |
| 2 | Límite de débito | `DEBIT` no puede exceder `$10,000.00` — `10000.00` exacto se **acepta**. `CREDIT` sin límite | `DEBIT_LIMIT_EXCEEDED` |
| 3 | Moneda | Sólo `MXN` | `UNSUPPORTED_CURRENCY` |

Los límites exactos son los casos frontera y están cubiertos por tests parametrizados. La redacción del enunciado es precisa —"mayor a" y "no pueden exceder"— y la implementación la respeta al pie de la letra.

Cada regla es una clase que implementa `TransactionRule` (patrón Strategy), compuestas por `TransactionRulesEngine`. Agregar una regla nueva no requiere modificar el motor (principio abierto/cerrado).

### Por qué `Currency` no es un `enum`

Si el tipo sólo admitiera `MXN`, una petición con `"USD"` fallaría al deserializar y devolvería `400`. El requisito pide que la moneda no soportada sea un **rechazo de negocio**, así que debe llegar al motor de reglas y salir como `422` con su código descriptivo.

En cambio `TransactionType` **sí** es un enum: `CREDIT|DEBIT` es un contrato sintáctico cerrado, y un valor fuera de ese conjunto es un request malformado (`400`), no una decisión de negocio. La distinción es deliberada.

---

## Máquina de estados

```
                    ┌──────────┐
                    │ PENDING  │◀────┐
                    └────┬─────┘     │ registerRetryAttempt()
           ┌─────────────┼───────────┴──────┐
           ▼             ▼                  ▼
    ┌────────────┐ ┌───────────┐    ┌────────────┐
    │  EXECUTED  │ │ REJECTED  │    │   FAILED   │
    └────────────┘ └───────────┘    └────────────┘
      proveedor      proveedor       reintentos
      APPROVED       rechazó         agotados (DLQ)
```

Los tres estados finales son terminales. Las transiciones se validan dentro del agregado: cualquier intento ilegal lanza `InvalidStateTransitionException` en lugar de corromper el registro silenciosamente.

`PENDING` es el estado más importante del diseño: cubre tanto el timeout como el escenario en que el proveedor respondiera `202 Accepted`. En ambos casos el resultado real es desconocido, y marcar la transacción como `EXECUTED` o `REJECTED` sería inventar información.

---

## Idempotencia

Contrato: header `Idempotency-Key` con un UUID generado por el cliente.

| Estado de la clave | Comportamiento |
|---|---|
| No existe | Se reserva con `SET key IN_PROGRESS NX EX 86400` y se procesa |
| Reservada, en curso | `409 Conflict` |
| Completada | Se recupera la transacción original y se devuelve sin llamar al proveedor |

### Manejo de errores en el decorador

| Resultado | Acción sobre la clave | Razón |
|---|---|---|
| `EXECUTED` o `PENDING` | Se marca completada | Resultado válido, replicable |
| Rechazo del proveedor (`422`) | **Se marca completada** | Resultado terminal: el replay debe devolver el mismo 422 sin volver a golpear al proveedor |
| Violación de regla local | **Se libera** | El cliente debe poder corregir y reintentar con la misma clave |
| Error inesperado | Se libera | No hay resultado que replicar |

Que un rechazo de negocio fije la clave es intencional: `INSUFFICIENT_FUNDS` es una respuesta legítima y definitiva del proveedor, no un fallo del que convenga reintentar.

Si no se envía `Idempotency-Key`, el decorador delega directamente sin tocar Redis. La garantía es opt-in.

---

## Resiliencia y reintentos

Dos capas con horizontes temporales distintos:

```
Resilience4j  →  cubre los MILISEGUNDOS
Kafka         →  cubre los MINUTOS
```

### Capa 1 — Resilience4j (en el adaptador HTTP)

| Parámetro | Valor |
|---|---|
| Connect timeout | 1 s |
| Read timeout | 2 s |
| Reintentos inmediatos | 2, backoff exponencial desde 200 ms |
| Circuit breaker | ventana de 20, umbral 50 %, 10 s abierto |

**`ProviderRejectedException` está en `ignore-exceptions`.** Un `INSUFFICIENT_FUNDS` es una respuesta *correcta* del proveedor; si abriera el circuito, un pico de cuentas sin fondos tumbaría la integración completa para todos los clientes. Es el detalle que separa una configuración copiada de una razonada.

El adaptador clasifica las respuestas HTTP explícitamente:

| Status | Interpretación |
|---|---|
| `2XX` | `Approved` |
| `408`, `429`, `5XX` | Transitorio → `ProviderUnavailableException` |
| Resto de `4XX` | `Rejected` (decisión de negocio) |
| Body ilegible | `ProviderUnavailableException` |

### Capa 2 — Kafka (retry topics + DLQ)

Cuando Resilience4j agota sus intentos, la transacción queda `PENDING` y se publica `TransactionRetryRequested`.

```
transactions.retry
    ├─ transactions.retry-0    (5 s)
    ├─ transactions.retry-1    (30 s)
    ├─ transactions.retry-2    (120 s)
    └─ transactions.retry-dlt  → marca FAILED, intervención manual
```

Configurado declarativamente con `@RetryableTopic`. El dominio no sabe que esto existe.

`ProviderRejectedException`, `BusinessRuleViolationException` y `TransactionNotFoundException` están en `exclude`: van directo a la DLQ sin consumir reintentos, porque ninguna se resolverá esperando.

El consumer usa `ErrorHandlingDeserializer`. Sin él, un mensaje corrupto provoca un bucle infinito de deserialización que bloquea la partición completa — uno de los fallos de producción más comunes con Kafka.

### Verificación end-to-end

```bash
docker compose stop mockoon
# POST /transactions -> 202 Accepted, status PENDING
docker compose start mockoon
# tras el backoff, el reintento la completa -> status EXECUTED
```

---

## Proveedor externo (mock)

Mockoon con **estado real**: los saldos se mantienen entre peticiones mediante global variables, sembrados desde un data bucket. Dos CREDIT consecutivos sobre la misma cuenta devuelven saldos crecientes, no un valor fijo.

```bash
curl -XPOST localhost:3001/provider/v1/execute -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-demo","type":"CREDIT","amount":1500,"currency":"MXN"}'   # balance: 11500
curl -XPOST localhost:3001/provider/v1/execute -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-demo","type":"DEBIT","amount":500,"currency":"MXN"}'     # balance: 11000

curl -XPOST localhost:3001/provider/v1/admin/reset    # reiniciar estado
```

El estado vive en memoria del proceso: reiniciar el contenedor lo borra.

### Escenarios por convención de `accountId`

| `accountId` | HTTP | `code` | Caso real |
|---|---|---|---|
| `acc-insufficient` | 402 | `INSUFFICIENT_FUNDS` | Saldo insuficiente |
| `acc-blocked` | 403 | `ACCOUNT_BLOCKED` | Bloqueo por riesgo |
| `acc-frozen` | 403 | `ACCOUNT_FROZEN` | Bloqueo judicial / PLD |
| `acc-notfound` | 404 | `ACCOUNT_NOT_FOUND` | Cuenta inexistente |
| `acc-duplicate` | 409 | `DUPLICATE_TRANSACTION` | Idempotencia del proveedor |
| `acc-limit` | 422 | `DAILY_LIMIT_EXCEEDED` | Límite diario agotado |
| `acc-throttled` | 429 | `RATE_LIMIT_EXCEEDED` | Throttling |
| `acc-error` | 500 | `INTERNAL_ERROR` | Fallo interno |
| `acc-down` | 503 | `SERVICE_UNAVAILABLE` | Proveedor caído |
| `acc-slow` | 200 | — | Latencia 5 s → dispara timeout |
| `acc-chaos` | 503 | — | Falla 1 de cada 5 peticiones |
| cualquier otro | 200 | — | `APPROVED` con saldo actualizado |

Los escenarios de error son invocables a voluntad en lugar de depender del historial de peticiones. Esto es deliberado: un escenario de prueba debe ser reproducible sin importar el orden de ejecución.

`responseMode` debe estar en `null` para que Mockoon evalúe las reglas en orden; con `RANDOM` elige respuestas al azar e ignora la convención.

### Por qué los tests no apuntan a este mock

Un mock con estado compartido introduce acoplamiento temporal: un test que pasa aislado falla dentro de la suite según lo que hayan hecho los anteriores. Mockoon sirve para exploración manual y demostración. Los tests automatizados usan stubs deterministas configurados por caso.

---

## Seguridad

- OAuth2 Resource Server con JWT firmado HS256.
- `SessionCreationPolicy.STATELESS` — permite escalado horizontal sin sticky sessions.
- Usuarios en memoria (`demo` / `demo`) y endpoint `/auth/token` para emisión.
- Validación al arranque de que el secreto tenga al menos 32 bytes, requisito de HS256.

| Ruta | Acceso |
|---|---|
| `/auth/token` | Público |
| `/actuator/health/**`, `/actuator/info` | Público |
| `/swagger-ui/**`, `/v3/api-docs/**` | Público |
| `/transactions/**` | Autenticado |

En producción, el secreto vendría de AWS Secrets Manager y la validación sería contra un JWKS externo emitido por un identity provider. Se usó HS256 con usuarios en memoria porque el reto pedía demostrar el flujo, no montar un IdP.

---

## Estrategia de pruebas

| Nivel | Herramientas | Alcance |
|---|---|---|
| Arquitectura | ArchUnit | Fronteras entre capas — rompe el build si se violan |
| Unitario (dominio) | JUnit 5, AssertJ | Reglas, value objects, máquina de estados. Sin Spring |
| Unitario (aplicación) | Mockito | Orquestación con puertos mockeados; decorador de idempotencia |
| Adaptador REST | `@WebMvcTest` | Serialización, validación, manejo de errores, códigos HTTP |
| Adaptador persistencia | `@DataJpaTest` + Testcontainers | Mapeo, filtros dinámicos, orden y paginación |
| Adaptador proveedor | WireMock | Clasificación de status, timeouts, circuit breaker |
| Integración | Testcontainers (Postgres + Redis + Kafka) | Flujo completo incluyendo reintentos |

Las pruebas de arquitectura convierten el diseño en algo ejecutable, no en una intención documentada:

```java
@ArchTest
static final ArchRule domain_is_framework_free = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta..", "org.apache.kafka..");
```

Los tests del dominio corren sin contexto de Spring: la suite se ejecuta en milisegundos. Es la recompensa directa de mantener el núcleo limpio.

Casos frontera cubiertos explícitamente: monto exactamente `1.00`, débito exactamente `10000.00`, moneda en minúsculas, montos con más de dos decimales, transiciones de estado ilegales, replay de rechazo, y fail-open de Redis.

---

## Consideraciones de alto volumen

El enunciado plantea millones de transacciones diarias.

**Implementado**

- `open-in-view: false` — evita mantener la conexión abierta durante la serialización JSON, que a este volumen agotaría el pool.
- Sin transacción de base de datos abierta durante la llamada HTTP externa.
- Pool de conexiones dimensionado con timeout agresivo.
- Índices alineados al patrón de acceso dominante.
- Batch inserts habilitados en Hibernate.
- Servicio *stateless*: escala horizontalmente sin coordinación.
- Particionado de Kafka por `accountId`: paralelismo sin perder orden por cuenta.

**Documentado, no implementado** (sería sobre-ingeniería para el alcance del reto)

- **Paginación por keyset (cursor).** `OFFSET 1000000` provoca un table scan. Con volúmenes reales, la paginación debería ir por cursor sobre `(created_at, id)`. Se implementó offset por simplicidad y compatibilidad con el contrato pedido (`page`, `limit`).
- **Particionado de tabla por rango de `created_at`.** Estrategia natural de crecimiento; permite archivar particiones antiguas sin tocar las activas.
- **Réplicas de lectura** para separar el tráfico de consulta del de escritura.
- **Outbox pattern.** Actualmente la publicación a Kafka ocurre tras el commit; un fallo entre ambos dejaría la transacción `PENDING` sin evento de reintento. Una tabla outbox eliminaría esa ventana. El log registra el fallo de publicación para que sea detectable.

---

## Despliegue

### Dockerfile

Multi-stage con tres etapas: build con Maven, extracción de capas de Spring Boot, y runtime sobre JRE 21 slim.

Decisiones relevantes:

- **Capas de Spring Boot** ordenadas por volatilidad: un cambio de código reconstruye sólo la capa `application` (~200 KB) en lugar de la imagen completa.
- **Usuario no-root** y `readOnlyRootFilesystem` — requisito de cualquier política de seguridad de pods.
- **`-XX:MaxRAMPercentage=75.0`** en lugar de `-Xmx` fijo, para que la JVM respete el límite del contenedor.
- `dependency:go-offline` en capa separada: las dependencias sólo se re-descargan si cambia el `pom.xml`.

### Kubernetes

```
deploy/k8s/base/
├── deployment.yaml   # probes, resources, securityContext
├── service.yaml
├── configmap.yaml
├── secret.example.yaml
├── hpa.yaml
└── kustomization.yaml
```

`startupProbe` separado de `livenessProbe`: sin esa separación, o el liveness es tan laxo que no detecta cuelgues, o mata el pod durante el arranque de la JVM. Es el bucle de reinicios clásico en despliegues Java sobre Kubernetes.

Los secretos se declaran como plantilla. En producción irían por External Secrets Operator contra AWS Secrets Manager.

### ECS Fargate

`deploy/ecs/task-definition.json` con secretos referenciados desde Secrets Manager y logs a CloudWatch.

### Terraform

`deploy/terraform/` provisiona ECR (con política de retención de 5 imágenes) y el rol de OIDC para GitHub Actions.

**Nota de costos — decisión consciente:** EKS cobra `$0.10/hora` por el control plane desde que existe, corra o no algo dentro; escalar los nodos a cero no lo evita. Para una demostración, ECS Fargate no tiene cargo fijo. Por eso el Terraform provisiona ECR e IAM pero **no** el clúster: si se quiere EKS, `eksctl create cluster --fargate` lo levanta en un comando sin gestionar estado adicional, y los manifiestos de `deploy/k8s/` funcionan igual en ambos destinos.

En el mismo sentido, Postgres, Redis y Kafka se despliegan dentro del clúster con volúmenes efímeros. RDS y sobre todo MSK (~$150/mes) consumirían los créditos rápidamente. En producción real irían gestionados.

---

## CI/CD

```
.github/workflows/
├── ci.yml    # build + test + JaCoCo — en PR y push a main
└── cd.yml    # ECR push + despliegue a ECS o EKS — manual o por tag
```

Optimizaciones para no consumir minutos innecesarios:

- **`concurrency` con `cancel-in-progress`** — cancela runs obsoletos cuando se empuja de nuevo a la misma rama.
- **`paths-ignore`** para cambios sólo en documentación o manifiestos.
- **Caché de dependencias Maven** vía `actions/setup-java`.
- **Caché de capas Docker** con `type=gha`.
- **Despliegues por `workflow_dispatch` o tag**, nunca en cada push — ahí es donde se queman los minutos gratuitos.

Autenticación con AWS vía **OIDC**: el workflow asume un rol temporal en lugar de guardar claves de acceso de larga vida en los secretos del repositorio.

---

## Uso de Inteligencia Artificial

Usé Claude (Anthropic) como copiloto técnico durante el desarrollo, en los siguientes puntos:

- **Contraste de arquitectura.** Discutí la estructura hexagonal, la ubicación del listener de Kafka como adaptador de entrada, y la separación entre rechazo de negocio y fallo técnico. Las decisiones finales son mías; la IA sirvió para presionar los argumentos.
- **Generación de boilerplate.** Configuración de Maven, `docker-compose`, environment de Mockoon, manifiestos de Kubernetes y workflows de GitHub Actions.
- **Revisión de casos frontera.** Validación de la interpretación literal de las reglas (`> 1.00` vs `>= 1.00`, `10000.00` exacto) y enumeración de escenarios de error realistas del proveedor.
- **Debugging.** Diagnóstico de errores de configuración durante la integración (ver abajo).
- **Redacción de documentación.** Estructura y revisión de este README.

### Problemas encontrados y resueltos durante la implementación

Estos aparecieron en la integración y valen como registro de lo que costó trabajo:

| Problema | Causa | Solución |
|---|---|---|
| `JwtEncodingException: Failed to select a JWK signing key` | `NimbusJwtEncoder` asume RS256 si no se especifica el header JWS; buscaba una clave RSA y sólo había una simétrica | `JwsHeader.with(MacAlgorithm.HS256)` explícito |
| `ClassCastException: TransactionRetryMessage cannot be cast to String` | El `value-serializer` por defecto del producer es `StringSerializer` | `JsonSerializer` + `spring.json.add.type.headers: false` |
| Escenarios de Mockoon devolviendo respuestas incorrectas | `responseMode: RANDOM` ignora las reglas y elige al azar | `responseMode: null` |
| `package com.tngtech.archunit... does not exist` | Test de arquitectura ubicado en `src/main/java`, donde las dependencias `test-scope` no están en el classpath | Mover a `src/test/java` |
| `@ConditionalOnMissingBean` sin efecto | Sólo se evalúa en métodos `@Bean` de clases `@Configuration`, no sobre `@Component` | Declarar el bean en `BeanConfig` |

Puedo explicar cada línea del código entregado y la razón detrás de cada decisión. Las secciones de "Decisiones de diseño" y "Consideraciones de alto volumen" reflejan ese razonamiento, incluyendo lo que decidí **no** implementar y por qué.

---

## Preguntas frecuentes sobre el diseño

**¿No es Kafka excesivo para este reto?**
Lo sería si sólo sirviera para "usar Kafka". Aquí resuelve un problema concreto: sin él, una transacción que falla por un `504` del proveedor queda `PENDING` y nadie se entera nunca. El reto pide manejar correctamente las respuestas 5XX, y "manejar" no puede significar "registrar y olvidar". La alternativa más simple habría sido un job programado que barra las `PENDING`; Kafka aporta backoff diferenciado y DLQ, además de ser el mecanismo que se usaría en producción.

**¿Por qué no usar el índice único de Postgres para idempotencia y evitar Redis?**
Funcionaría. La razón es el volumen: sería una escritura adicional en el camino crítico de cada petición, más un job de purga que Redis resuelve con TTL. A escala menor, el índice único sería la opción correcta y más simple.

**¿Por qué duplicar el modelo entre dominio y JPA?**
Porque las anotaciones de JPA imponen restricciones —constructor sin argumentos, campos mutables, ausencia de invariantes en el constructor— que son incompatibles con un agregado que protege su estado. El mapper es el precio de que `Transaction` pueda garantizar que un `EXECUTED` nunca vuelva a `PENDING`.

**¿Qué pasa si el proveedor ejecuta el cargo pero la respuesta se pierde?**
La transacción queda `PENDING` y se reintenta. El reintento podría duplicar el cargo, y ahí es donde entra la idempotencia — pero la garantía real requiere que el proveedor también la soporte, propagando la `Idempotency-Key` en la llamada saliente. El mock no la implementa, así que el diseño actual protege contra reintentos del *cliente*, no contra reintentos *hacia el proveedor*. Es una limitación conocida; la mitigación completa es el índice único sobre `provider_transaction_id` más reconciliación.

**¿Por qué `202` y no `500` cuando el proveedor está caído?**
Porque la petición sí fue aceptada y sí será procesada. Devolver `500` le diría al cliente que reintente, generando exactamente el duplicado que se quiere evitar. `202 Accepted` con estado `PENDING` comunica la verdad: recibida, resultado aún desconocido, consultable después.

---

## Qué haría con más tiempo

- **Reconciliación de transacciones `PENDING`.** El proveedor podría responder `202 Accepted` ("recibida, resultado desconocido"). Ese caso deja la transacción en el mismo limbo que un timeout, y es el problema más difícil de las integraciones de pagos reales. La solución correcta es polling de estado o un webhook de confirmación. Está contemplado en la máquina de estados pero no implementado: quedaba fuera del alcance.
- **Propagar `Idempotency-Key` al proveedor** para cerrar el hueco de duplicación descrito arriba.
- **Outbox pattern** para eliminar la ventana entre commit y publicación del evento.
- **Paginación por cursor** en `GET /transactions`.
- **Métricas de negocio** en Prometheus: tasa de rechazo por código, latencia del proveedor por percentil, profundidad de la DLQ.
- **Rate limiting** por cuenta como defensa adicional.
- **Contract testing** (Pact) contra el proveedor externo.

---

## Estructura del repositorio

```
.
├── .github/workflows/     # CI y CD
├── deploy/
│   ├── k8s/base/          # manifiestos Kubernetes
│   ├── ecs/               # task definition Fargate
│   └── terraform/         # ECR + OIDC
├── docker/mockoon/        # environment del proveedor simulado
├── src/
│   ├── main/java/         # código de aplicación
│   ├── main/resources/    # configuración + migraciones Flyway
│   └── test/java/         # suite de pruebas
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```