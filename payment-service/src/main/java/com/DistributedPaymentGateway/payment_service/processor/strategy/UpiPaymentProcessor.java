package com.DistributedPaymentGateway.payment_service.processor.strategy;

import com.DistributedPaymentGateway.common_lib.dto.PaymentProcessorRequest;
import com.DistributedPaymentGateway.common_lib.util.RandomizerUtil;
import com.DistributedPaymentGateway.payment_service.processor.PaymentProcessor;
import com.DistributedPaymentGateway.common_lib.dto.PaymentProcessorResponse;
import org.springframework.stereotype.Component;

@Component
public class UpiPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentProcessorResponse charge(PaymentProcessorRequest request) {
        final String VPA_CODE_FAIL = "fail@okaxis";

        String bankCode = request.methodDetails() != null ?
                request.methodDetails().get("vpa").toString() : null;

        // Simulation rule: force failure for a specific test VPA.
        if(VPA_CODE_FAIL.equals(bankCode)){
            return new PaymentProcessorResponse.Failure("BANK_REJECTED",
                    "Bank rejected");
        }

        String processorRef = "UPI_PROCESSOR_" + RandomizerUtil.randomBase64(16);
//        String bankRef = "BANK_REF" + RandomizerUtil.randomBase64(16);
        return new PaymentProcessorResponse.Pending(processorRef);
    }
}
