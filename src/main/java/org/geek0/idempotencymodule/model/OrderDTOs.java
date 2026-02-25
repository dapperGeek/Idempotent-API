package org.geek0.idempotencymodule.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderDTOs {

    // ── Request ───────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {

        @NotBlank(message = "customerId is required")
        private String customerId;

        @NotBlank(message = "productId is required")
        private String productId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.01", message = "unitPrice must be positive")
        private BigDecimal unitPrice;
    }

    // ── Response ──────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {

        private Long id;
        private String customerId;
        private String productId;
        private Integer quantity;
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createdAt;

        /**
         * Whether this response was replayed from a previous identical request.
         * Clients can use this to understand the response came from cache.
         */
        private boolean idempotentReplay;

        public static OrderResponse fromOrder(Order order, boolean replay) {
            return OrderResponse.builder()
                    .id(order.getId())
                    .customerId(order.getCustomerId())
                    .productId(order.getProductId())
                    .quantity(order.getQuantity())
                    .totalAmount(order.getTotalAmount())
                    .status(order.getStatus().name())
                    .createdAt(order.getCreatedAt())
                    .idempotentReplay(replay)
                    .build();
        }
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private LocalDateTime timestamp;
    }
}