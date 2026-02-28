package org.geek0.idempotencymodule.service;

import lombok.Builder;
import lombok.Getter;

/**
 * Wraps the result of an idempotent operation, including metadata
 * about whether it came from cache or was freshly computed.
 */
@Getter
@Builder
public class IdempotencyResult<T> {

    /** The actual business result (order, payment, etc.). */
    private final T result;

    /** True if this response was served from cache (duplicate request). */
    private final boolean fromCache;

    /** The HTTP status to return to the client. */
    private final int httpStatus;
}