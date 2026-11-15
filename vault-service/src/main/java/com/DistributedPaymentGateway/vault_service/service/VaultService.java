package com.DistributedPaymentGateway.vault_service.service;


import com.DistributedPaymentGateway.common_lib.dto.PaymentProcessorResponse;
import com.DistributedPaymentGateway.common_lib.entity.Money;
import com.DistributedPaymentGateway.vault_service.dto.request.TokenizeRequest;
import com.DistributedPaymentGateway.vault_service.dto.response.TokenizeResponse;

import java.util.Map;
import java.util.UUID;

public interface VaultService {
    // Tokenize raw card input and return a reusable token reference.
    TokenizeResponse tokenize(TokenizeRequest request, UUID merchantId);

    PaymentProcessorResponse charge(UUID paymentId, String token, Money amount, Map<String, Object> methodDetails);
}
