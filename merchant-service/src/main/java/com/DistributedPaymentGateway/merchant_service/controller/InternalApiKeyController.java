package com.DistributedPaymentGateway.merchant_service.controller;

import com.DistributedPaymentGateway.common_lib.cache.ApiKeyCacheEntry;
import com.DistributedPaymentGateway.common_lib.exception.ResourceNotFoundException;
import com.DistributedPaymentGateway.merchant_service.entity.ApiKey;
import com.DistributedPaymentGateway.merchant_service.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/api-keys")
public class InternalApiKeyController {

    private final ApiKeyRepository apiKeyRepository;

    @GetMapping("/{keyId}")
    public ApiKeyCacheEntry findByKeyId(@PathVariable String keyId) {
        ApiKey apiKey = apiKeyRepository.findByKeyId(keyId)
                .orElseThrow(()-> new ResourceNotFoundException("API_KEY_NOT_FOUND", "API Key with id " + keyId + " not found"));

        return new ApiKeyCacheEntry(
                        apiKey.getKeyId(),
                        apiKey.getKeySecretHash(),
                        apiKey.getPreviousKeySecretHash(),
                        apiKey.getGracePeriodExpiresAt(),
                        apiKey.getMerchant().getId(),
                        apiKey.getEnvironment(),
                        apiKey.isEnabled()
                );
    }
}
