package com.ravi.orbit.entity;

import com.ravi.orbit.enums.EPaymentMethod;
import com.ravi.orbit.enums.EPaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "payment_tbl")
public class Payment extends UIDBase {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id", nullable = false)
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private EPaymentStatus paymentStatus = EPaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private EPaymentMethod paymentMethod;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "transaction_uuid", unique = true)
    private String transactionUuid;

    @Column(name = "transaction_code")
    private String transactionCode;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "provider_status")
    private String providerStatus;

}