package com.DistributedPaymentGateway.merchant_service.dto.request;

import com.DistributedPaymentGateway.common_lib.enums.Environment;

public record CreateApiKeyRequest(
        Environment environment
) {
}
