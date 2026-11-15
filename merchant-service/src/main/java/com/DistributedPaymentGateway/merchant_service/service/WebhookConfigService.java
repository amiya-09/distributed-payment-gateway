package com.DistributedPaymentGateway.merchant_service.service;


import com.DistributedPaymentGateway.merchant_service.dto.request.UpdateWebhookConfigRequest;
import com.DistributedPaymentGateway.merchant_service.dto.response.WebhookConfigResponse;

import java.util.List;
import java.util.UUID;

public interface WebhookConfigService {

    WebhookConfigResponse create(UUID merchantId, UpdateWebhookConfigRequest request);
    List<WebhookConfigResponse> findAllByMerchantId(UUID merchantId);
    WebhookConfigResponse findById(UUID merchantId, UUID configId);
    WebhookConfigResponse update(UUID merchantId, UUID configId, UpdateWebhookConfigRequest request);
    void delete(UUID merchantId, UUID configId);

}
