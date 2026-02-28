package org.geek0.idempotencymodule.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persists the result of a previously processed request, keyed by
 * the client-supplied Idempotency-Key header.
 *
 * When a duplicate request arrives with the same key, we return
 * this cached response instead of re-executing the operation.
 */
@Entity
@Table(
        name = "idempotency_records",
        indexes = @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true)
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The unique key sent by the client (e.g., UUID). */
    @Column(nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    /** The HTTP method this key is bound to (safety check). */
    @Column(nullable = false, length = 10)
    private String httpMethod;

    /** The request path this key is bound to (safety check). */
    @Column(nullable = false, length = 500)
    private String requestPath;

    /** Serialized JSON of the response body. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    /** The HTTP status code of the original response. */
    @Column(nullable = false)
    private Integer responseStatus;

    /** When this record was first created. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** When this record expires and can be cleaned up. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}