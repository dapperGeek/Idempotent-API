package org.geek0.idempotencymodule.controller;

import org.geek0.idempotencymodule.model.Order;
import org.geek0.idempotencymodule.model.OrderDTOs.*;
import org.geek0.idempotencymodule.service.IdempotencyResult;
import org.geek0.idempotencymodule.service.IdempotencyService;
import org.geek0.idempotencymodule.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Order Controller — Idempotency Case Study.
 *
 * <p>This controller demonstrates three types of idempotency:
 *
 * <ol>
 *   <li><b>POST /api/orders</b> — Non-idempotent by nature, made safe via
 *       the Idempotency-Key header + IdempotencyService.</li>
 *   <li><b>DELETE /api/orders/{id}</b> — Naturally idempotent (cancelling an
 *       already-cancelled order is a no-op).</li>
 *   <li><b>GET /api/orders/{id}</b> — Naturally idempotent (reads don't mutate state).</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    /**
     * ════════════════════════════════════════════════════════════════════════
     * POST /api/orders — Create a new order (NON-idempotent by default)
     * ════════════════════════════════════════════════════════════════════════
     *
     * Requires an {@code Idempotency-Key} header (UUID reorgmended).
     *
     * <p>If this key has been seen before:
     * <ul>
     *   <li>The cached response is returned immediately.</li>
     *   <li>No new order is created in the database.</li>
     *   <li>The response body contains {@code idempotentReplay: true}.</li>
     * </ul>
     *
     * <p>geek0:
     * <pre>
     * POST /api/orders
     * Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
     * Content-Type: application/json
     *
     * { "customerId": "C001", "productId": "P001", "quantity": 2, "unitPrice": 49.99 }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        log.info("POST /api/orders | Idempotency-Key: {}", idempotencyKey);

        IdempotencyResult<OrderResponse> result = idempotencyService.executeIdempotently(
                idempotencyKey,
                "POST",
                "/api/orders",
                OrderResponse.class,

                // This lambda only runs if the key is NEW
                () -> {
                    Order created = orderService.createOrder(
                            request.getCustomerId(),
                            request.getProductId(),
                            request.getQuantity(),
                            request.getUnitPrice()
                    );
                    return OrderResponse.fromOrder(created, false);
                },
                HttpStatus.CREATED.value()
        );

        // Stamp the response to tell clients whether this was replayed
        OrderResponse body = result.getResult();
        body.setIdempotentReplay(result.isFromCache());

        HttpStatus status = result.isFromCache()
                ? HttpStatus.OK            // 200 for replays (already created)
                : HttpStatus.CREATED;      // 201 for first-time creation

        return ResponseEntity.status(status).body(body);
    }

    /**
     * ════════════════════════════════════════════════════════════════════════
     * DELETE /api/orders/{id} — Cancel an order (NATURALLY idempotent)
     * ════════════════════════════════════════════════════════════════════════
     *
     * HTTP DELETE is naturally idempotent. Cancelling an already-cancelled
     * order is a safe no-op. No Idempotency-Key header is needed here.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        log.info("DELETE /api/orders/{} — naturally idempotent", id);
        Order cancelled = orderService.cancelOrder(id);
        return ResponseEntity.ok(OrderResponse.fromOrder(cancelled, false));
    }

    /**
     * ════════════════════════════════════════════════════════════════════════
     * GET /api/orders — List all orders (NATURALLY idempotent)
     * ════════════════════════════════════════════════════════════════════════
     */
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /**
     * ════════════════════════════════════════════════════════════════════════
     * GET /api/orders/{id} — Get a single order (NATURALLY idempotent)
     * ════════════════════════════════════════════════════════════════════════
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }
}