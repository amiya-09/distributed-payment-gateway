package com.DistributedPaymentGateway.payment_service.gateway.dto;


import com.DistributedPaymentGateway.common_lib.entity.Money;
import com.DistributedPaymentGateway.common_lib.enums.PaymentMethod;

import java.util.Map;
import java.util.UUID;


public record PaymentRequest(
        UUID paymentId,
        UUID orderId,
        UUID merchantId,
        Money amount,
        PaymentMethod paymentMethod,
        Map<String, Object> methodDetails
) {

}
