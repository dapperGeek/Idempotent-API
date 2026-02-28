package org.geek0.idempotencymodule.service;

import org.geek0.idempotencymodule.model.Order;
import org.geek0.idempotencymodule.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Creates a new order. This method has a side effect (inserting a DB row)
     * and is intentionally NOT idempotent by itself.
     *
     * Idempotency is enforced at the controller layer via IdempotencyService,
     * which wraps this call and only executes it once per unique Idempotency-Key.
     */
    @Transactional
    public Order createOrder(String customerId, String productId, Integer quantity, BigDecimal unitPrice) {
        log.info("Executing createOrder — customerId={}, productId={}, qty={}", customerId, productId, quantity);

        // Simulate some business logic (e.g., price calculation, inventory check)
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));

        Order order = Order.builder()
                .customerId(customerId)
                .productId(productId)
                .quantity(quantity)
                .totalAmount(total)
                .status(Order.OrderStatus.PENDING)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created with ID={}", saved.getId());
        return saved;
    }

    /**
     * Cancels an order — idempotent by nature.
     * Calling it multiple times on an already-cancelled order is safe.
     */
    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Idempotent: if already cancelled, just return without error
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            log.info("Order {} is already cancelled. No-op.", orderId);
            return order;
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }
}