package com.DistributedPaymentGateway.vault_service.dto.response;


import com.DistributedPaymentGateway.common_lib.enums.CardBrand;

public record TokenizeResponse (
        // Return the generated token and card display metadata.
        // Clients use this token in later card-payment API calls.
        String token,
        String lastFour,
        CardBrand brand,
        Integer expiryMonth,
        Integer expiryYear
) {
}
