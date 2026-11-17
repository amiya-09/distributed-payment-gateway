package com.DistributedPaymentGateway.common_lib.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// Sent from vault-service to payment-service as JSON. The "type" property tells
// Jackson which record to build; without it an interface can't be deserialized.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaymentProcessorResponse.Pending.class, name = "PENDING"),
        @JsonSubTypes.Type(value = PaymentProcessorResponse.Success.class, name = "SUCCESS"),
        @JsonSubTypes.Type(value = PaymentProcessorResponse.Failure.class, name = "FAILURE")
})
public sealed interface PaymentProcessorResponse permits
        PaymentProcessorResponse.Pending,
        PaymentProcessorResponse.Success,
        PaymentProcessorResponse.Failure {

    record Pending(String processorReference)  implements PaymentProcessorResponse {}
    record Success(String processorReference, String bankReference)  implements PaymentProcessorResponse {}
    record Failure(String errorCode, String errorDescription)  implements PaymentProcessorResponse {}
}
