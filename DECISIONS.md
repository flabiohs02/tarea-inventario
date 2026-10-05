# DECISIONS

## Arquitectura

Clean Architecture ligera. Dominio y aplicación no dependen de ningún framework; Spring Boot vive solo en infraestructura:

```
com.store.inventory
├── api/              Contrato (no se modifica)
├── domain/           Reglas de negocio puras
│   ├── Product, StockReservation, ReservationStatus
│   ├── CategoryPolicy, CategoryPolicies   ← reglas por categoría
│   └── LowStockRule                       ← cuándo avisar a compras
├── application/      Casos de uso
│   ├── InventoryReservationService        ← implementa InventoryService
│   ├── LowStockNotifier, KeyedLocks
│   └── port/ ProductRepository, ReservationRepository
├── infrastructure/   Detalles reemplazables
│   ├── InventoryApplication               ← arranque de Spring Boot
│   ├── config/   InventoryProperties (application.properties), InventoryConfiguration, OpenApiConfiguration
│   ├── web/      InventoryController, ApiExceptionHandler, dto/
│   ├── notification/ LoggingStockAlertListener (canal provisional)
│   ├── InMemoryProductRepository, InMemoryReservationRepository
│   └── CompositeStockAlertListener        ← varios canales de aviso
└── Inventory         Composition root (único lugar que arma las piezas)
```

Las dependencias van hacia adentro: infraestructura → aplicación → dominio.

`Inventory.create(Clock, StockAlertListener)` sigue funcionando sin Spring y usa las reglas por defecto (`CategoryPolicies.defaults()`). La aplicación Spring usa la sobrecarga `Inventory.create(clock, listener, policies, lowStockRule)` con las reglas leídas de `application.properties`.

### Cómo cambiar las reglas de negocio

| Cambio | Dónde |
|---|---|
| Nueva categoría de temporada | Agregar el valor a `ProductCategory`, su bloque `inventory.categories.<CATEGORIA>.*` en `application.properties` y su política en `CategoryPolicies.defaults()` (la que usa `Inventory.create` sin Spring). Si falta alguno, la aplicación no arranca. |
| Cambiar el tiempo para pagar o el límite por pedido | `inventory.categories.<CATEGORIA>.payment-window` / `max-units-per-order` (sin valor = sin límite) |
| Cambiar el umbral de stock bajo | `inventory.low-stock-threshold` |
| Nuevo canal de aviso (SMS, Slack…) | Implementar `StockAlertListener` y añadirlo al composite en `InventoryConfiguration.stockAlertListener()`. |
| Pasar a base de datos | Implementar los dos puertos en `infrastructure` y conectarlos en `Inventory`. |

Las properties también se pueden sobrescribir sin recompilar, con variables de entorno (`INVENTORY_LOWSTOCKTHRESHOLD=3`) o con argumentos (`--inventory.low-stock-threshold=3`).

## API REST y Swagger

```bash
mvn spring-boot:run
# Swagger UI:  http://localhost:8080/swagger-ui.html
# OpenAPI:     http://localhost:8080/v3/api-docs
```

| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/api/products` `{sku, category}` | 201 |
| POST | `/api/products/{sku}/stock` `{quantity}` | 204 |
| GET | `/api/products/{sku}/availability` | 200 `{sku, available}` |
| POST | `/api/reservations` `{orderId, sku, quantity}` | 201 `{orderId, sku, quantity, expiresAt}` |
| POST | `/api/reservations/{orderId}/confirmation` | 204 |

Los errores se devuelven como `ProblemDetail` (RFC 9457):

| Caso | HTTP |
|---|---|
| Datos inválidos o producto no registrado al agregar stock | 400 |
| Stock insuficiente | 409 |
| Pedido sin reserva activa, o mismo `orderId` con otros datos | 409 |
| Límite por pedido de la categoría excedido | 422 |

Un reenvío idéntico de `POST /api/reservations` vuelve a responder 201 con la misma reserva.

## Supuestos

1. **Reenvíos de la app (idempotencia por `orderId`).**
   - Si se repite `reserve` con el mismo `orderId`, sku y cantidad mientras la reserva sigue activa o ya se confirmó, se devuelve la misma reserva sin reservar de nuevo. Esto pasa aunque ya no quede stock y no cambia la fecha de expiración.
   - Si se repite el mismo `orderId` con otro sku u otra cantidad, se lanza `IllegalStateException`.
   - Si la reserva del pedido ya expiró, el pedido puede reservar de nuevo (por ejemplo, el cliente vuelve a enviar el carrito).
   - Repetir `confirm` sobre un pedido ya confirmado no hace nada, porque el aviso de pago también puede llegar duplicado.
2. **Expiración.** Una reserva vence cuando `now >= expiresAt`. La expiración es *lazy*: las reservas vencidas dejan de contar al calcular `available`, sin hilos ni schedulers. Todo usa el `Clock` inyectado.
3. **Confirmar tarde.** Confirmar una reserva vencida lanza `IllegalStateException`, porque las unidades ya pudieron venderse a otro cliente. Qué hacer con ese pago (reembolso, reintento) le toca al flujo de pagos.
4. **Aviso de stock bajo.**
   - Se envía cuando las unidades disponibles quedan en 5 o menos después de una reserva o de un `addStock`. Esto incluye un producto nuevo que entra con poco stock.
   - Se envía una sola vez y solo se rearma cuando un `addStock` deja más de 5 unidades disponibles.
   - Las unidades que vuelven por reservas expiradas no cuentan como reabastecimiento.
   - Confirmar una reserva no cambia las unidades disponibles, así que no dispara avisos.
5. **Un aviso que falla no rompe la reserva del cliente.** El error se registra con `System.Logger`. El composite también aísla cada canal.
6. **Registrar un producto dos veces** con la misma categoría no hace nada. Con otra categoría lanza `IllegalStateException`, para no cambiar las reglas de reservas que ya existen.
7. **Producto desconocido:** `available` = 0 y `reserve` lanza `InsufficientStockException` (como dice el javadoc). `addStock` lanza `IllegalArgumentException`.
8. **Orden de validaciones en `reserve`:** cantidad > 0, luego límite de la categoría y luego stock disponible.

## Concurrencia

- Hay un lock por SKU para todo leer-validar-escribir sobre el stock, así que dos clientes no pueden reservar la misma unidad. Productos distintos no se bloquean entre sí.
- `reserve` y `confirm` toman primero un lock por `orderId` y luego el del SKU, siempre en ese orden para evitar deadlocks. Así los reintentos simultáneos del mismo pedido reservan una sola vez.
- Los avisos se envían fuera de los locks.
- `ConcurrencyTest` lanza 200 clientes contra 50 unidades y verifica que haya exactamente 50 reservas, cero sobreventa y un solo aviso.

## Lo que quedó fuera

- Persistencia real: todo vive en memoria, como pide el enunciado.
- Cancelación explícita de una reserva (por ejemplo, el cliente abandona el pago) y devoluciones de unidades vendidas. El contrato no las tiene.
- Limpieza de reservas vencidas y de locks viejos: en memoria crecen sin límite.
- `findActiveBySku` recorre todas las reservas, lo cual es O(n). Alcanza para el alcance actual.
- Métricas, trazas y logs estructurados.

## Qué cambiaría antes de producción

1. **Base de datos y varias instancias.** Los locks en memoria solo protegen una instancia. Con varias instancias:
   - Reservar con una operación atómica en BD, por ejemplo una transacción con `SELECT ... FOR UPDATE` sobre el producto, o bloqueo optimista con una columna `version`.
   - Poner una restricción única sobre `order_id` para garantizar la idempotencia entre instancias.
   - Indexar `(sku, status, expires_at)` para calcular las unidades disponibles.
2. **Avisos confiables:** usar un patrón *outbox*, es decir, guardar el aviso en la misma transacción y enviarlo de forma asíncrona. Así no se pierde si el proceso cae y no retrasa la reserva. El flag `lowStockAlerted` se guardaría con el producto.
3. **Reglas configurables:** pasar tiempos, límites y umbral a configuración o BD, para que marketing cree categorías sin desplegar. Hoy `ProductCategory` es un enum del contrato y obliga a recompilar.
4. **Job de limpieza** para archivar reservas vencidas.
5. **Observabilidad:** métricas de reservas creadas, expiradas y confirmadas, rechazos por stock y avisos enviados o fallidos.
6. Acordar con el equipo de la app un **idempotency key** explícito y qué respuesta espera cuando hay conflicto (mismo `orderId` con otros datos).

## Cómo verificar

```bash
mvn test
```

| Suite | Qué cubre |
|---|---|
| `InventoryServiceTest` | Tests originales |
| `ReservationLifecycleTest` | Reservar, expirar (bordes exactos), liberar, confirmar, confirmación tardía o duplicada |
| `CategoryRulesTest` | Tiempo para pagar y límite por pedido de cada categoría |
| `IdempotencyTest` | Reenvíos de la app y conflictos de `orderId` |
| `LowStockAlertTest` | Umbral, no repetición, rearme, canal caído, varios canales |
| `ValidationTest` | Entradas inválidas y productos desconocidos |
| `ConcurrencyTest` | Sin sobreventa bajo carga, reintentos concurrentes |
| `CategoryPoliciesTest` | Falla rápido si una categoría no tiene reglas |
