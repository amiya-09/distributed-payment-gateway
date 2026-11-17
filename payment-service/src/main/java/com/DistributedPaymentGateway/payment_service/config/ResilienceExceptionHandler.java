package com.DistributedPaymentGateway.payment_service.config;

import com.DistributedPaymentGateway.common_lib.exception.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * An open circuit breaker means a downstream service is unavailable, not that this
 * service is broken: answer 503 with Retry-After instead of falling through to the
 * shared handler's 500. Ordered first so it wins over GlobalExceptionHandler's catch-all.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ResilienceExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("Rejected by open circuit breaker: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "10")
                .body(ErrorResponse.of("DEPENDENCY_UNAVAILABLE",
                        "A downstream service is temporarily unavailable, please retry shortly"));
    }
}
