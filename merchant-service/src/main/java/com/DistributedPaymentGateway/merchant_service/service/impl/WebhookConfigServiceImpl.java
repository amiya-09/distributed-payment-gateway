package com.DistributedPaymentGateway.merchant_service.service.impl;

import com.DistributedPaymentGateway.common_lib.exception.ResourceNotFoundException;
import com.DistributedPaymentGateway.common_lib.util.RandomizerUtil;
import com.DistributedPaymentGateway.merchant_service.dto.request.UpdateWebhookConfigRequest;
import com.DistributedPaymentGateway.merchant_service.dto.response.WebhookConfigResponse;
import com.DistributedPaymentGateway.merchant_service.entity.Merchant;
import com.DistributedPaymentGateway.merchant_service.entity.MerchantWebhookConfig;
import com.DistributedPaymentGateway.merchant_service.mapper.WebhookConfigMapper;
import com.DistributedPaymentGateway.merchant_service.repository.MerchantRepository;
import com.DistributedPaymentGateway.merchant_service.repository.WebhookConfigRepository;
import com.DistributedPaymentGateway.merchant_service.service.WebhookConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookConfigServiceImpl implements WebhookConfigService {

    private final MerchantRepository merchantRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final BytesEncryptor bytesEncryptor;
    private final WebhookConfigMapper webhookConfigMapper;

    @Override
    public WebhookConfigResponse create(UUID merchantId, UpdateWebhookConfigRequest request) {
        // When we get a request to create a webhook config, we want to also create a secret, and we will share that secret
        // with the merchant, and we also want to store that secret but not as is. First we want to encrypt the secret
        // and then store it in the database. We will use a hash function to encrypt the secret and then store the hash
        // in the database. We will also store the secret in a secure vault, so that we can retrieve it later
        // when we need to send a webhook to the merchant.
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("MERCHANT_NOT_FOUND", "Merchant with id " + merchantId + " not found"));

        String rawSecret = RandomizerUtil.randomBase64(32);
        byte[] rawSecretBytes = rawSecret.getBytes(StandardCharsets.UTF_8);

        String encryptedSecret = Base64.getEncoder().encodeToString(bytesEncryptor.encrypt(rawSecretBytes));

        MerchantWebhookConfig config = MerchantWebhookConfig.builder()
                .merchant(merchant)
                .targetUrl(request.targetUrl())
                .webhookSecret(encryptedSecret)
                .enabled(true)
                .eventTypes(request.eventTypes())
                .build();

        config = webhookConfigRepository.save(config);
        return webhookConfigMapper.toResponse(config, rawSecret);
    }

    @Override
    public List<WebhookConfigResponse> findAllByMerchantId(UUID merchantId) {
        return webhookConfigRepository.findByMerchant_Id(merchantId).stream()
                .map(config -> webhookConfigMapper.toResponse(config, null))
                .toList();
    }

    @Override
    public WebhookConfigResponse findById(UUID merchantId, UUID configId) {
        MerchantWebhookConfig config = requireOwnedConfig(merchantId, configId);
        return webhookConfigMapper.toResponse(config, null);
    }

    @Override
    @Transactional
    public WebhookConfigResponse update(UUID merchantId, UUID configId, UpdateWebhookConfigRequest request) {
        MerchantWebhookConfig config = requireOwnedConfig(merchantId, configId);
        config.setTargetUrl(request.targetUrl());
        config.setEventTypes(request.eventTypes());
        config = webhookConfigRepository.save(config);
        log.info("Webhook config with id {} was updated for merchant {}", configId, merchantId);
        return webhookConfigMapper.toResponse(config, null);
    }

    @Override
    @Transactional
    public void delete(UUID merchantId, UUID configId) {
        MerchantWebhookConfig config = requireOwnedConfig(merchantId, configId);
        webhookConfigRepository.delete(config);
        log.info("Webhook config with id {} was deleted", configId);
    }

    private MerchantWebhookConfig requireOwnedConfig(UUID merchantId, UUID configId) {
        return webhookConfigRepository.findByIdAndMerchant_Id(configId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("WEBHOOK_CONFIG_NOT_FOUND", "Webhook config with id " + configId + " not found for merchant " + merchantId));
    }
}
