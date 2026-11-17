package com.DistributedPaymentGateway.payment_service.gateway.adapter;

import com.DistributedPaymentGateway.common_lib.dto.VaultChargeRequest;
import com.DistributedPaymentGateway.payment_service.client.VaultServiceClient;
import com.DistributedPaymentGateway.payment_service.gateway.PaymentAdapter;
import com.DistributedPaymentGateway.payment_service.gateway.dto.PaymentRequest;
import com.DistributedPaymentGateway.payment_service.gateway.dto.PaymentResult;
import com.DistributedPaymentGateway.common_lib.dto.PaymentProcessorResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CardPaymentAdapter implements PaymentAdapter {

    private final VaultServiceClient vaultServiceClient;

    @Override
    // No @Retry: vault's /charge is not idempotent. If a call times out, the first charge may
    // still go through, and a retry would charge the card a second time.
    @CircuitBreaker(name = "vault-service")
    public PaymentResult initiate(PaymentRequest request){
        // Read token generated during card tokenization.
        String token = (String) request.methodDetails().get("token");
        PaymentProcessorResponse response = vaultServiceClient.charge(
                new VaultChargeRequest(
                        request.paymentId(),
                        token,
                        request.amount(),
                        request.methodDetails()
                )
        );
        return switch (response) {
            case PaymentProcessorResponse.Success success -> new PaymentResult.Success(success.bankReference());
            case PaymentProcessorResponse.Failure failure -> new PaymentResult.Failure(failure.errorCode(), failure.errorDescription());
            case PaymentProcessorResponse.Pending pending -> new PaymentResult.Pending(pending.processorReference());
        };
    }

    @Override
    public PaymentResult capture(UUID paymentId) {
        return new PaymentResult.Success("CARD_REF");
    }
}
