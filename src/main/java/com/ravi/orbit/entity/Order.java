package com.ravi.orbit.entity;

import com.ravi.orbit.enums.EOrderStatus;
import com.ravi.orbit.enums.EOrderPaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "order_tbl")
public class Order extends UIDBase {

    private static final long serialVersionUID = 1L;

    @Column(name = "order_number")
    private Long orderNumber;

    @Column(name = "total_items")
    private int totalItems;

    @Column(name = "total_market_price")
    private BigDecimal totalMarketPrice;

    @Column(name = "total_discount")
    private BigDecimal totalDiscount;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private EOrderStatus orderStatus = EOrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private EOrderPaymentStatus paymentStatus = EOrderPaymentStatus.UNPAID;

    @Column(name = "order_date")
    private LocalDateTime orderDate = LocalDateTime.now();

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate = orderDate.plusDays(3);     // delivery date = 3 days of order date

    @Column(name = "stock_released", nullable = false)
    private boolean stockReleased = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "id")
    private User customer;

    @Column(name = "customer_id", insertable = false, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_image")
    private String customerImage;

}
