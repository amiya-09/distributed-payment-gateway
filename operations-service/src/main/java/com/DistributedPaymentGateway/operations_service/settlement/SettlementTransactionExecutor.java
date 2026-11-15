package com.DistributedPaymentGateway.operations_service.settlement;

import com.DistributedPaymentGateway.common_lib.dto.PaymentSettlementView;
import com.DistributedPaymentGateway.common_lib.dto.SettlementBankDetails;
import com.DistributedPaymentGateway.common_lib.entity.Money;
import com.DistributedPaymentGateway.common_lib.enums.EventAggregateType;
import com.DistributedPaymentGateway.common_lib.enums.SettlementStatus;
import com.DistributedPaymentGateway.common_lib.exception.ResourceNotFoundException;
import com.DistributedPaymentGateway.operations_service.client.MerchantServiceClient;
import com.DistributedPaymentGateway.operations_service.client.PaymentServiceClient;
import com.DistributedPaymentGateway.operations_service.entity.Settlement;
import com.DistributedPaymentGateway.operations_service.entity.SettlementPayment;
import com.DistributedPaymentGateway.operations_service.entity.SettlementPaymentId;
import com.DistributedPaymentGateway.operations_service.outbox.OutboxEventPublisher;
import com.DistributedPaymentGateway.operations_service.repository.SettlementPaymentRepository;
import com.DistributedPaymentGateway.operations_service.repository.SettlementRepository;
import com.DistributedPaymentGateway.operations_service.settlement.dto.BankTransferResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementTransactionExecutor {

    private static final double FEE_RATE = 0.02;
    private static final double GST_RATE = 0.18;

    private final SettlementRepository settlementRepository;
    private final SettlementPaymentRepository settlementPaymentRepository;
    private final BankTransferProcessor bankTransferProcessor;
    private final OutboxEventPublisher outboxEventPublisher;
    private final SettlementIntegrationGateway settlementIntegrationGateway;

    @Transactional
    public void processForMerchant(UUID merchantId, LocalDate settlementDate) {

        log.info("Processing settlement for merchantId: {}, settlementDate: {}", merchantId, settlementDate);
        // Implement the logic to process settlement for the given merchant and date.
        List<PaymentSettlementView> unsettledPayments = settlementIntegrationGateway.findUnsettledCaptured(merchantId);
        if (unsettledPayments.isEmpty()) return;

        log.info("Processing {} unsettled payments for merchantId: {}, settlementDate: {}", unsettledPayments.size(), merchantId, settlementDate);

        Integer grossAmount = unsettledPayments.stream()
                .map(PaymentSettlementView::amountUnits)
                .reduce(Integer::sum)
                .orElse(0);

        Money gross = Money.of(grossAmount, unsettledPayments.getFirst().currency());

        int fee = Math.toIntExact(Math.round(gross.getAmountUnits() * FEE_RATE));
        int gst = Math.toIntExact(Math.round(fee * GST_RATE));
        Money feeAmount = Money.of(fee, gross.getCurrency());
        Money gstAmount = Money.of(gst, gross.getCurrency());
        Money netAmount = gross.subtract(feeAmount).subtract(gstAmount);

        Settlement settlement = Settlement.builder()
                .merchantId(merchantId)
                .grossAmount(gross)
                .feeAmount(feeAmount)
                .gstAmount(gstAmount)
                .netAmount(netAmount)
                .status(SettlementStatus.INITIATED)
                .build();

        settlementRepository.save(settlement);

        try {// this list contains all the payments that are part of the settlement
            List<SettlementPayment> links = new ArrayList<>();
            for (PaymentSettlementView payment : unsettledPayments) {
                links.add(SettlementPayment.builder()
                        .id(new SettlementPaymentId(settlement.getId(), payment.paymentId()))
                        .settlement(settlement)
                        .build());
            }
            settlementPaymentRepository.saveAll(links);

            SettlementBankDetails settlementBankDetails = settlementIntegrationGateway.getSettlementBankDetails(merchantId);

            // call the bank transfer service to transfer net amount to merchant settlement bank details
            BankTransferResult bankTransferResult = bankTransferProcessor.initiate(settlement.getId(), merchantId,
                    netAmount, settlementBankDetails.accountNumber(), settlementBankDetails.ifscCode());

            settlement.setStatus(SettlementStatus.TRANSFER_PENDING);
            settlement.setBankReference(bankTransferResult.registrationRef());
            settlementRepository.save(settlement);
        } catch (Exception e) {
            log.error("Error while processing settlement for merchantId: {}, settlementDate: {}", merchantId, settlementDate, e);
            settlement.setStatus(SettlementStatus.FAILED);
            settlementRepository.save(settlement);
        }

    }

    @Transactional
    public void resolveTransfer(UUID settlementId, String errorCode, String errorDescription) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", settlementId));

        if(settlement.getStatus() != SettlementStatus.TRANSFER_PENDING) {
            log.info("Settlement resolved, skipping for id: {}", settlement.getId());
            return;
        }

        if(errorCode == null){
            settlement.setStatus(SettlementStatus.PROCESSED);
            settlement.setProcessedAt(LocalDateTime.now());
            settlementRepository.save(settlement);

            List<SettlementPayment> settlementPaymentList = settlementPaymentRepository.findBySettlement(settlement);
            List<UUID> paymentIds = settlementPaymentList.stream()
                    .map(SettlementPayment::getId)
                    .map(SettlementPaymentId::getPaymentId)
                    .toList();
            settlementIntegrationGateway.markSettled(paymentIds);

            log.info("Settlement processed successfully, settlement id: {}", settlement.getId());
            outboxEventPublisher.publish(EventAggregateType.SETTLEMENT, settlementId,
                    "SETTLEMENT_PROCESSED", Map.of(
                            "settlementId", settlementId.toString(),
                            "merchantId", settlement.getMerchantId(),
                            "status", settlement.getStatus().name(),
                            "settlementAmount", settlement.getNetAmount().getAmountUnits(),
                            "settlementCurrency", settlement.getNetAmount().getCurrency()
                    ));
        } else {
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailureReason(errorCode+" : "+errorDescription);
            settlementRepository.save(settlement);
            log.warn("Settlement failed, settlement id: {}", settlement.getId());
            outboxEventPublisher.publish(EventAggregateType.SETTLEMENT, settlementId,
                    "SETTLEMENT_FAILED", Map.of(
                            "settlementId", settlementId.toString(),
                            "merchantId", settlement.getMerchantId(),
                            "status", settlement.getStatus().name(),
                            "settlementAmount", settlement.getNetAmount().getAmountUnits(),
                            "settlementCurrency", settlement.getNetAmount().getCurrency()
                    ));
        }

    }

}
