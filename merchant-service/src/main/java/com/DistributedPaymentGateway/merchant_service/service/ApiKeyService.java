package com.DistributedPaymentGateway.merchant_service.service;



import com.DistributedPaymentGateway.merchant_service.dto.request.CreateApiKeyRequest;
import com.DistributedPaymentGateway.merchant_service.dto.response.ApiKeyCreateResponse;
import com.DistributedPaymentGateway.merchant_service.dto.response.ApiKeyResponse;

import java.util.List;
import java.util.UUID;

public interface ApiKeyService {
    ApiKeyCreateResponse createApiKey(UUID merchantId, CreateApiKeyRequest request);

    List<ApiKeyResponse> getAllApiKeys(UUID merchantId);

    void revoke(UUID merchantId, UUID keyId);

    ApiKeyCreateResponse rotate(UUID merchantId, UUID keyId);
}
