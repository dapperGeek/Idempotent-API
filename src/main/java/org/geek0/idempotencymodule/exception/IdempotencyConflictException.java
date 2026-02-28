package org.geek0.idempotencymodule.exception;

/**
 * Thrown when the same Idempotency-Key is used with a different
 * request path or HTTP method — indicating client misuse.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}