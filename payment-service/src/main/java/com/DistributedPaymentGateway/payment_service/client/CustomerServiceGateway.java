package com.DistributedPaymentGateway.payment_service.client;

import com.DistributedPaymentGateway.common_lib.dto.FindOrCreateCustomerRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Wraps the merchant-service customer call in the circuit breaker and retry.
 * Only this remote call is covered, so errors in the rest of order creation
 * (duplicate receipt, DB constraints) don't count as merchant-service failures.
 */
@Component
@RequiredArgsConstructor
public class CustomerServiceGateway {

    private final CustomerServiceClient customerServiceClient;

    @CircuitBreaker(name = "merchant-service")
    @Retry(name = "merchant-service")
    public UUID findOrCreate(FindOrCreateCustomerRequest request) {
        return customerServiceClient.findOrCreate(request);
    }
}
