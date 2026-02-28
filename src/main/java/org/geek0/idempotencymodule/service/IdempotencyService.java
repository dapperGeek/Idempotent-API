package org.geek0.idempotencymodule.service;

import org.geek0.idempotencymodule.exception.IdempotencyConflictException;
import org.geek0.idempotencymodule.model.IdempotencyRecord;
import org.geek0.idempotencymodule.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Core Idempotency Service.
 *
 * <p>This service is the heart of the idempotency mechanism. Its contract:
 * <ol>
 *   <li>If the given key has NEVER been seen — execute the operation, persist
 *       the result, return it.</li>
 *   <li>If the key HAS been seen and the operation succeeded — return the
 *       cached response WITHOUT re-executing the operation.</li>
 *   <li>If the key is reused with a DIFFERENT endpoint — throw a conflict error
 *       to protect against accidental key reuse.</li>
 * </ol>
 *
 * <p>This guarantees that no matter how many times a client retries the same
 * request (network timeouts, 5xx errors, etc.), the side effect (e.g., charging
 * a card, creating an order) happens exactly once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    @Value("${idempotency.ttl-minutes:1440}")
    private int ttlMinutes;

    /**
     * Execute an operation idempotently.
     *
     * @param idempotencyKey The client-supplied unique key (UUID recommended).
     * @param httpMethod     The HTTP method of the incoming request (e.g., "POST").
     * @param requestPath    The request path (e.g., "/api/orders").
     * @param resultType     The expected return type class.
     * @param operation      The actual business logic to execute if not cached.
     * @param <T>            Return type.
     * @return The result — either from cache or freshly computed.
     */
    @Transactional
    public <T> IdempotencyResult<T> executeIdempotently(
            String idempotencyKey,
            String httpMethod,
            String requestPath,
            Class<T> resultType,
            Supplier<T> operation,
            int httpStatus
    ) {
        // ── Step 1: Check if we've seen this key before ───────────────────────
        Optional<IdempotencyRecord> existing = recordRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            log.info("Duplicate request detected for key: {}. Returning cached response.", idempotencyKey);

            // Safety check: same key must match the same endpoint
            if (!record.getHttpMethod().equals(httpMethod) || !record.getRequestPath().equals(requestPath)) {
                throw new IdempotencyConflictException(
                        String.format(
                                "Idempotency key '%s' was previously used for [%s %s] but is now being " +
                                        "used for [%s %s]. Keys must not be reused across different endpoints.",
                                idempotencyKey,
                                record.getHttpMethod(), record.getRequestPath(),
                                httpMethod, requestPath
                        )
                );
            }

            // Deserialize and return the cached response
            T cachedResult = deserialize(record.getResponseBody(), resultType);
            return IdempotencyResult.<T>builder()
                    .result(cachedResult)
                    .fromCache(true)
                    .httpStatus(record.getResponseStatus())
                    .build();
        }

        // ── Step 2: Key is new — execute the real operation ───────────────────
        log.info("New idempotency key: {}. Executing operation.", idempotencyKey);
        T result = operation.get();

        // ── Step 3: Persist the result so future duplicates get cached ────────
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .httpMethod(httpMethod)
                .requestPath(requestPath)
                .responseBody(serialize(result))
                .responseStatus(httpStatus)
                .expiresAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .build();

        recordRepository.save(record);
        log.info("Persisted idempotency record for key: {}", idempotencyKey);

        return IdempotencyResult.<T>builder()
                .result(result)
                .fromCache(false)
                .httpStatus(httpStatus)
                .build();
    }

    /**
     * Scheduled cleanup of expired idempotency records.
     * Runs every hour. In production, consider a database TTL or cron job instead.
     */
    @Scheduled(fixedRateString = "PT1H")
    @Transactional
    public void cleanupExpiredRecords() {
        int deleted = recordRepository.deleteExpiredRecords(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency records.", deleted);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize idempotency response", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize cached idempotency response", e);
        }
    }
}