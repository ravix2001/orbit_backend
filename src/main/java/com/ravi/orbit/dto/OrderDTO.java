package com.ravi.orbit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ravi.orbit.enums.EOrderStatus;
import com.ravi.orbit.enums.EOrderPaymentStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDTO {

    public OrderDTO(UUID id, Long orderNumber, int totalItems, BigDecimal totalMarketPrice,
                    BigDecimal totalDiscount, BigDecimal totalAmount, EOrderStatus orderStatus,
                    EOrderPaymentStatus paymentStatus, LocalDateTime orderDate, LocalDateTime deliveryDate,
                    UUID customerId, String customerName, String customerPhone, String customerImage) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.totalItems = totalItems;
        this.totalMarketPrice = totalMarketPrice;
        this.totalDiscount = totalDiscount;
        this.totalAmount = totalAmount;
        this.orderStatus = orderStatus;
        this.paymentStatus = paymentStatus;
        this.orderDate = orderDate.toLocalDate();
        this.deliveryDate = deliveryDate.toLocalDate();
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerImage = customerImage;
//        this.sellerId = sellerId;
//        this.sellerName = sellerName;
//        this.sellerPhone = sellerPhone;
//        this.sellerImage = sellerImage;
//        this.productId = productId;
    }

    public OrderDTO(UUID id, Long orderNumber, int totalItems, BigDecimal totalAmount, EOrderStatus orderStatus,
                    EOrderPaymentStatus paymentStatus, LocalDateTime orderDate, LocalDateTime deliveryDate,
                    UUID customerId, String customerName, String customerPhone, String customerImage) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.totalItems = totalItems;
        this.totalAmount = totalAmount;
        this.orderStatus = orderStatus;
        this.paymentStatus = paymentStatus;
        this.orderDate = orderDate.toLocalDate();
        this.deliveryDate = deliveryDate.toLocalDate();
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerImage = customerImage;
    }

    private UUID id;

    private Long orderNumber;

    private int totalItems;

    private BigDecimal totalMarketPrice;

    private BigDecimal totalDiscount;

    private BigDecimal totalAmount;

    private EOrderStatus orderStatus;

    private EOrderPaymentStatus paymentStatus;

    private LocalDate orderDate;

    private LocalDate deliveryDate;

    private UUID customerId;

    private String customerName;

    private String customerPhone;

    private String customerImage;

    private UserDTO customer;

//    private UUID sellerId;
//
//    private String sellerName;
//
//    private String sellerPhone;
//
//    private String sellerImage;
//
//    private UserDTO seller;
//
    private UUID productId;

    private ProductDTO product;

    private List<OrderProductRequestDTO> products;

    private Map<UUID, Integer> variantQuantities; // variantId -> quantity

    private List<OrderItemDTO> orderItems;

}
