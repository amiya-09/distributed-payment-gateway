package com.DistributedPaymentGateway.merchant_service.entity;

import com.DistributedPaymentGateway.common_lib.entity.BaseEntity;
import com.DistributedPaymentGateway.common_lib.enums.BusinessType;
import com.DistributedPaymentGateway.common_lib.enums.MerchantStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "merchant", indexes = {
        @Index(name = "idx_merchant_status", columnList = "status")
})
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 20)
    private String contactNumber;

    @Column(length = 200)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    private BusinessType businessType;

    @Column(length = 100)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status = MerchantStatus.PENDING_KYC;

    @Column(length = 20)
    private String gstId;

    @Column(length = 20)
    private String panId;

    @Column(length = 20)
    private String settlementBankAccount;

    @Column(length = 11)
    private String settlementBankIfsc;

    @Column(length = 100)
    private String settlementBankAccountHolderName;
}
