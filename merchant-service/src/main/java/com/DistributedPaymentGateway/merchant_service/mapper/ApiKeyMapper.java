package com.DistributedPaymentGateway.merchant_service.mapper;


import com.DistributedPaymentGateway.merchant_service.dto.response.ApiKeyCreateResponse;
import com.DistributedPaymentGateway.merchant_service.dto.response.ApiKeyResponse;
import com.DistributedPaymentGateway.merchant_service.entity.ApiKey;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ApiKeyMapper {
    ApiKeyCreateResponse toApiKeyCreateResponse(ApiKey apiKey);

    List<ApiKeyResponse> toApiKeyResponses(List<ApiKey> apiKeyList);
}
