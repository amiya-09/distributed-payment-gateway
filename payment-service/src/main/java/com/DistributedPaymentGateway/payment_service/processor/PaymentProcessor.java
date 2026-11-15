package com.DistributedPaymentGateway.payment_service.processor;


import com.DistributedPaymentGateway.common_lib.dto.PaymentProcessorResponse;

public interface PaymentProcessor {

    PaymentProcessorResponse charge(PaymentProcessorRequest request);
}
