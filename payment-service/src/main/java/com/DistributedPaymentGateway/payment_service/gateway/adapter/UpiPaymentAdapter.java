package com.DistributedPaymentGateway.payment_service.gateway.adapter;

import com.DistributedPaymentGateway.common_lib.dto.PaymentProcessorRequest;
import com.DistributedPaymentGateway.common_lib.enums.PaymentMethod;
import com.DistributedPaymentGateway.payment_service.gateway.PaymentAdapter;
import com.DistributedPaymentGateway.payment_service.gateway.dto.PaymentRequest;
import com.DistributedPaymentGateway.payment_service.gateway.dto.PaymentResult;
import com.DistributedPaymentGateway.payment_service.processor.PaymentProcessorRouter;
import com.DistributedPaymentGateway.common_lib.dto.PaymentProcessorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class UpiPaymentAdapter implements PaymentAdapter {

    private final PaymentProcessorRouter paymentProcessorRouter;

    @Override
    public PaymentResult initiate(PaymentRequest request) {
        log.info("Initiating UPI payment for request: {}", request);
        try {
            PaymentProcessorRequest paymentProcessorRequest = PaymentProcessorRequest.nonCard(request.paymentId(), PaymentMethod.UPI,
                    request.amount(), request.methodDetails());
            PaymentProcessorResponse paymentProcessorResponse =
                    paymentProcessorRouter.charge(paymentProcessorRequest);
            // Convert processor response into gateway-level result.
            return switch (paymentProcessorResponse) {
                case PaymentProcessorResponse.Failure failure ->
                        new PaymentResult.Failure(failure.errorCode(), failure.errorDescription());
                case PaymentProcessorResponse.Success success -> new PaymentResult.Success(success.bankReference());
                case PaymentProcessorResponse.Pending pending -> new PaymentResult.Pending(pending.processorReference());
            };
        } catch (Exception e) {
            log.warn("UPI failed, paymentId: {}", request.paymentId());
            return new PaymentResult.Failure("UPI_FAILED", e.getMessage());
        }
    }

    @Override
    public PaymentResult capture(UUID paymentId) {
        return new PaymentResult.Success("UPI_REF");
    }
}
