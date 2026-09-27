package com.ravi.orbit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravi.orbit.config.EsewaProperties;
import com.ravi.orbit.dto.EsewaPaymentInitiateResponse;
import com.ravi.orbit.dto.EsewaPaymentRequest;
import com.ravi.orbit.dto.EsewaPaymentResponse;
import com.ravi.orbit.dto.EsewaStatusResponse;
import com.ravi.orbit.entity.Order;
import com.ravi.orbit.entity.OrderItem;
import com.ravi.orbit.entity.Payment;
import com.ravi.orbit.entity.Product;
import com.ravi.orbit.entity.ProductVariant;
import com.ravi.orbit.enums.EOrderPaymentStatus;
import com.ravi.orbit.enums.EOrderStatus;
import com.ravi.orbit.enums.EPaymentMethod;
import com.ravi.orbit.enums.EPaymentStatus;
import com.ravi.orbit.exceptions.BadRequestException;
import com.ravi.orbit.repository.OrderItemRepository;
import com.ravi.orbit.repository.OrderRepository;
import com.ravi.orbit.repository.PaymentRepository;
import com.ravi.orbit.repository.ProductRepository;
import com.ravi.orbit.repository.ProductVariantRepository;
import com.ravi.orbit.service.IEsewaPaymentService;
import com.ravi.orbit.utils.EsewaSignatureUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EsewaPaymentServiceImpl implements IEsewaPaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    private final EsewaProperties esewaProperties;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

    @Override
    public EsewaPaymentInitiateResponse initiatePayment(
            EsewaPaymentRequest request
    ) {

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() ->
                        new BadRequestException("Order not found"));

        /*
         * Only PENDING orders can start eSewa payment.
         */
        if (order.getOrderStatus() != EOrderStatus.PENDING) {
            throw new BadRequestException(
                    "Only pending orders can be paid"
            );
        }

        /*
         * Prevent payment attempts after the order has already been paid.
         */
        if (order.getPaymentStatus() == EOrderPaymentStatus.PAID) {
            throw new BadRequestException(
                    "Order is already paid"
            );
        }

        /*
         * Do not allow multiple active eSewa transactions
         * for the same order.
         *
         * This is important because stock is reserved at
         * order creation, not at payment initiation.
         */
        boolean existingPendingPayment =
                paymentRepository
                        .findByOrderIdAndPaymentStatus(
                                order.getId(),
                                EPaymentStatus.PENDING
                        )
                        .isPresent();

        if (existingPendingPayment) {
            throw new BadRequestException(
                    "A payment is already in progress for this order"
            );
        }

        BigDecimal totalAmount = order.getTotalAmount();

        String transactionUuid =
                "ORDER-" +
                        order.getOrderNumber() +
                        "-" +
                        UUID.randomUUID()
                                .toString()
                                .replace("-", "");

        String amount =
                totalAmount
                        .stripTrailingZeros()
                        .toPlainString();

        String signature =
                EsewaSignatureUtil.generateSignature(
                        amount,
                        transactionUuid,
                        esewaProperties.getProductCode(),
                        esewaProperties.getSecretKey()
                );

        /*
         * Create our internal payment record BEFORE
         * redirecting the customer to eSewa.
         */
        Payment payment = new Payment();

        payment.setOrder(order);
        payment.setPaymentStatus(EPaymentStatus.PENDING);
        payment.setPaymentMethod(EPaymentMethod.ESEWA);
        payment.setAmount(totalAmount);
        payment.setTransactionUuid(transactionUuid);
        payment.setProductCode(
                esewaProperties.getProductCode()
        );

        paymentRepository.save(payment);

        return EsewaPaymentInitiateResponse.builder()
                .paymentUrl(
                        esewaProperties.getPaymentUrl()
                )
                .amount(amount)
                .taxAmount("0")
                .totalAmount(amount)
                .transactionUuid(transactionUuid)
                .productCode(
                        esewaProperties.getProductCode()
                )
                .productServiceCharge("0")
                .productDeliveryCharge("0")
                .successUrl(
                        esewaProperties.getSuccessCallbackUrl()
                )
                .failureUrl(
                        esewaProperties.getFailureCallbackUrl()
                )
                .signedFieldNames(
                        "total_amount,transaction_uuid,product_code"
                )
                .signature(signature)
                .build();
    }

    @Override
    public void handleSuccess(String encodedData) {

        EsewaPaymentResponse response =
                decodeResponse(encodedData);

        /*
         * eSewa response signature must be verified.
         */
        verifySignature(response);

        Payment payment =
                paymentRepository
                        .findByTransactionUuid(
                                response.getTransactionUuid()
                        )
                        .orElseThrow(() ->
                                new BadRequestException(
                                        "Payment not found"
                                ));

        /*
         * Idempotency:
         *
         * eSewa/browser can potentially hit the callback more
         * than once. Do not process a successful payment twice.
         */
        if (payment.getPaymentStatus()
                == EPaymentStatus.SUCCESS) {
            return;
        }

        validatePaymentResponse(payment, response);

        /*
         * Browser callback is NOT trusted as final payment
         * confirmation.
         *
         * Ask eSewa server-to-server for the actual status.
         */
        EsewaStatusResponse statusResponse =
                getTransactionStatus(payment);

        /*
         * If eSewa's status service is temporarily unavailable,
         * DO NOT return HTTP 400.
         *
         * Leave payment PENDING.
         * Scheduler will reconcile it later.
         */
        if (statusResponse == null
                || statusResponse.getStatus() == null) {

            payment.setProviderStatus("PENDING");
            paymentRepository.save(payment);

            return;
        }

        String status =
                statusResponse.getStatus()
                        .trim()
                        .toUpperCase();

        switch (status) {

            case "COMPLETE":

                finalizeSuccessfulPayment(
                        payment,
                        response,
                        statusResponse
                );

                break;

            case "PENDING":
            case "AMBIGUOUS":
            case "AMBIGIOUS":

                /*
                 * Payment may still complete.
                 * Do not release stock.
                 * Scheduler will reconcile it.
                 */
                payment.setProviderStatus(status);
                paymentRepository.save(payment);

                break;

            case "CANCELED":
            case "NOT_FOUND":

                /*
                 * Definitively unsuccessful.
                 */
                markPaymentFailedAndReleaseStock(
                        payment,
                        status
                );

                break;

            default:

                /*
                 * Unknown provider state.
                 * Safest behavior is to leave it pending.
                 */
                payment.setProviderStatus(status);
                paymentRepository.save(payment);

                break;
        }
    }

    @Override
    public void handleFailure(String encodedData) {

        /*
         * eSewa failure_url can be called with no useful payload.
         *
         * If there is no payload, we cannot safely identify the
         * payment. Leave it PENDING. The scheduler will reconcile it.
         */
        if (encodedData == null
                || encodedData.isBlank()) {
            return;
        }

        EsewaPaymentResponse response;

        try {
            response = decodeResponse(encodedData);
        } catch (Exception e) {
            /*
             * Do not release stock merely because a browser sent
             * an invalid/empty failure payload.
             *
             * Scheduler will reconcile the payment.
             */
            return;
        }

        Payment payment =
                paymentRepository
                        .findByTransactionUuid(
                                response.getTransactionUuid()
                        )
                        .orElse(null);

        if (payment == null) {
            return;
        }

        if (payment.getPaymentStatus()
                != EPaymentStatus.PENDING) {
            return;
        }

        /*
         * If eSewa supplied signature information, verify it.
         *
         * Do not require it here because the failure callback
         * may not contain the same response data as success.
         */
        if (response.getSignature() != null
                && response.getSignedFieldNames() != null) {

            verifySignature(response);
        }

        /*
         * Validate fields that we can safely validate.
         */
        validatePaymentResponse(payment, response);

        /*
         * IMPORTANT:
         *
         * failure_url does NOT necessarily mean FAILED.
         * eSewa documents that it can also represent PENDING.
         *
         * Therefore query eSewa directly.
         */
        EsewaStatusResponse statusResponse =
                getTransactionStatus(payment);

        if (statusResponse == null
                || statusResponse.getStatus() == null) {
            return;
        }

        String status =
                statusResponse.getStatus()
                        .trim()
                        .toUpperCase();

        switch (status) {

            case "COMPLETE":
                /*
                 * The payment actually succeeded.
                 */
                finalizeSuccessfulPayment(
                        payment,
                        response,
                        statusResponse
                );
                break;

            case "CANCELED":
            case "NOT_FOUND":
                /*
                 * Payment is definitively unsuccessful.
                 */
                markPaymentFailedAndReleaseStock(
                        payment,
                        status
                );
                break;

            case "PENDING":
            case "AMBIGUOUS":
            case "AMBIGIOUS":
                /*
                 * Do NOT release stock.
                 *
                 * Transaction may still complete.
                 * Scheduler will check again.
                 */
                payment.setProviderStatus(status);
                paymentRepository.save(payment);
                break;

            default:
                /*
                 * Unknown provider state.
                 *
                 * Safest behavior is to leave payment pending.
                 */
                payment.setProviderStatus(status);
                paymentRepository.save(payment);
                break;
        }
    }

    /**
     * Finalizes a verified successful payment.
     *
     * Stock is NOT decremented here because stock was already
     * reserved when the order was created.
     */
    private void finalizeSuccessfulPayment(
            Payment payment,
            EsewaPaymentResponse response,
            EsewaStatusResponse statusResponse
    ) {

        if (payment.getPaymentStatus()
                == EPaymentStatus.SUCCESS) {
            return;
        }

        payment.setPaymentStatus(
                EPaymentStatus.SUCCESS
        );

        payment.setTransactionCode(
                response.getTransactionCode()
        );

        payment.setProviderStatus(
                statusResponse.getStatus()
        );

        paymentRepository.save(payment);

        Order order = payment.getOrder();

        order.setPaymentStatus(
                EOrderPaymentStatus.PAID
        );

        order.setOrderStatus(
                EOrderStatus.PLACED
        );

        orderRepository.save(order);
    }

    /**
     * Marks payment failed and releases the stock reservation.
     *
     * This operation is idempotent through Order.stockReleased.
     */
    private void markPaymentFailedAndReleaseStock(
            Payment payment,
            String providerStatus
    ) {

        if (payment.getPaymentStatus()
                != EPaymentStatus.PENDING) {
            return;
        }

        payment.setPaymentStatus(
                EPaymentStatus.FAILED
        );

        payment.setProviderStatus(
                providerStatus
        );

        paymentRepository.save(payment);

        Order order = payment.getOrder();

        /*
         * If another business process already changed the order,
         * do not blindly overwrite it.
         */
        if (order.getOrderStatus() == EOrderStatus.PENDING) {

            order.setPaymentStatus(
                    EOrderPaymentStatus.UNPAID
            );

            order.setOrderStatus(
                    EOrderStatus.CANCELLED
            );
        }

        releaseReservedStock(order);

        orderRepository.save(order);
    }

    /**
     * Releases the stock reserved during order creation.
     *
     * IMPORTANT:
     *
     * This method must only run after the payment is definitively
     * unsuccessful.
     *
     * Order.stockReleased protects against:
     *
     * callback -> scheduler -> duplicate callback
     *
     * restoring the stock multiple times.
     */
    private void releaseReservedStock(Order order) {

        if (order.isStockReleased()) {
            return;
        }

        List<OrderItem> orderItems =
                orderItemRepository.findAllByOrderId(
                        order.getId()
                );

        for (OrderItem item : orderItems) {

            ProductVariant variant =
                    productVariantRepository
                            .findById(item.getVariantId())
                            .orElseThrow(() ->
                                    new BadRequestException(
                                            "Product variant not found: "
                                                    + item.getVariantId()
                                    ));

            Product product =
                    productRepository
                            .findById(item.getProductId())
                            .orElseThrow(() ->
                                    new BadRequestException(
                                            "Product not found: "
                                                    + item.getProductId()
                                    ));

            /*
             * Return the reserved quantity.
             */
            variant.setQuantity(
                    variant.getQuantity()
                            + item.getQuantity()
            );

            /*
             * Once quantity is returned, the variant becomes
             * available again.
             */
            variant.setIsAvailable(true);

            product.setQuantity(
                    product.getQuantity()
                            + item.getQuantity()
            );

            productVariantRepository.save(variant);
            productRepository.save(product);
        }

        /*
         * Mark the reservation as released AFTER restoring
         * all stock.
         */
        order.setStockReleased(true);
    }

    /**
     * Validates data returned by eSewa against our own Payment.
     */
    private void validatePaymentResponse(
            Payment payment,
            EsewaPaymentResponse response
    ) {

        if (response.getTransactionUuid() == null
                || !payment.getTransactionUuid()
                .equals(response.getTransactionUuid())) {

            throw new BadRequestException(
                    "Invalid transaction UUID"
            );
        }

        if (response.getProductCode() == null
                || !payment.getProductCode()
                .equals(response.getProductCode())) {

            throw new BadRequestException(
                    "Invalid product code"
            );
        }

        if (response.getTotalAmount() == null) {
            throw new BadRequestException(
                    "Invalid payment amount"
            );
        }

        BigDecimal paidAmount =
                parseAmount(
                        response.getTotalAmount()
                );

        if (payment.getAmount()
                .compareTo(paidAmount) != 0) {

            throw new BadRequestException(
                    "Payment amount mismatch"
            );
        }
    }

    /**
     * Decodes eSewa's Base64 response.
     */
    private EsewaPaymentResponse decodeResponse(
            String encodedData
    ) {

        try {

            byte[] decoded =
                    Base64.getDecoder()
                            .decode(encodedData);

            String json =
                    new String(
                            decoded,
                            StandardCharsets.UTF_8
                    );

            return objectMapper.readValue(
                    json,
                    EsewaPaymentResponse.class
            );

        } catch (Exception e) {

            throw new BadRequestException(
                    "Invalid eSewa response"
            );
        }
    }

    /**
     * Verifies the HMAC signature supplied by eSewa.
     */
    private void verifySignature(
            EsewaPaymentResponse response
    ) {

        if (response.getSignature() == null
                || response.getSignedFieldNames() == null) {

            throw new BadRequestException(
                    "Invalid eSewa response"
            );
        }

        String expected =
                generateResponseSignature(
                        response,
                        esewaProperties.getSecretKey()
                );

        boolean valid =
                MessageDigest.isEqual(
                        expected.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        response.getSignature()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        if (!valid) {
            throw new BadRequestException(
                    "Invalid eSewa signature"
            );
        }
    }

    /**
     * Generates the response signature using the exact
     * signed_field_names order supplied by eSewa.
     */
    private String generateResponseSignature(
            EsewaPaymentResponse response,
            String secretKey
    ) {

        Map<String, String> fields =
                Map.of(
                        "transaction_code",
                        nullToEmpty(
                                response.getTransactionCode()
                        ),

                        "status",
                        nullToEmpty(
                                response.getStatus()
                        ),

                        "total_amount",
                        nullToEmpty(
                                response.getTotalAmount()
                        ),

                        "transaction_uuid",
                        nullToEmpty(
                                response.getTransactionUuid()
                        ),

                        "product_code",
                        nullToEmpty(
                                response.getProductCode()
                        ),

                        "signed_field_names",
                        nullToEmpty(
                                response.getSignedFieldNames()
                        )
                );

        String message =
                Arrays.stream(
                                response
                                        .getSignedFieldNames()
                                        .split(",")
                        )
                        .map(String::trim)
                        .map(field ->
                                field
                                        + "="
                                        + fields.getOrDefault(
                                        field,
                                        ""
                                )
                        )
                        .collect(
                                Collectors.joining(",")
                        );

        return EsewaSignatureUtil
                .generateHmacSha256(
                        message,
                        secretKey
                );
    }

    /**
     * Server-to-server transaction verification.
     */
    private EsewaStatusResponse getTransactionStatus(
            Payment payment
    ) {

        String amount =
                payment.getAmount()
                        .stripTrailingZeros()
                        .toPlainString();

        try {

            return restClient
                    .get()
                    .uri(
                            esewaProperties
                                    .getStatusUrl()
                                    + "?product_code={productCode}"
                                    + "&total_amount={totalAmount}"
                                    + "&transaction_uuid={transactionUuid}",

                            payment.getProductCode(),
                            amount,
                            payment.getTransactionUuid()
                    )
                    .retrieve()
                    .body(EsewaStatusResponse.class);

        } catch (Exception e) {

            /*
             * Do not convert an eSewa verification-service
             * problem into HTTP 400.
             *
             * The payment remains PENDING and the scheduler
             * will retry reconciliation.
             */
            return null;
//            throw new BadRequestException(
//                    "Unable to verify transaction with eSewa"
//            );
        }
    }

    private BigDecimal parseAmount(String amount) {

        try {

            return new BigDecimal(
                    amount
                            .replace(",", "")
                            .trim()
            );

        } catch (Exception e) {

            throw new BadRequestException(
                    "Invalid payment amount"
            );
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Transactional
    public void reconcilePendingPayment(Payment payment) {

        /*
         * Payment may have been completed since the scheduler
         * fetched it.
         */
        if (payment.getPaymentStatus()
                != EPaymentStatus.PENDING) {
            return;
        }

        EsewaStatusResponse statusResponse =
                getTransactionStatus(payment);

        if (statusResponse == null
                || statusResponse.getStatus() == null) {
            return;
        }

        String status =
                statusResponse.getStatus()
                        .trim()
                        .toUpperCase();

        switch (status) {

            case "COMPLETE":

                /*
                 * Scheduler does not have the browser response,
                 * so construct the minimum response needed to
                 * finalize the payment.
                 */
                EsewaPaymentResponse response =
                        new EsewaPaymentResponse();

                response.setTransactionUuid(
                        payment.getTransactionUuid()
                );

                response.setProductCode(
                        payment.getProductCode()
                );

                response.setTotalAmount(
                        payment.getAmount()
                                .stripTrailingZeros()
                                .toPlainString()
                );

                response.setStatus("COMPLETE");

                /*
                 * transaction_code may be available in the
                 * status response depending on your DTO.
                 *
                 * If your EsewaStatusResponse has it, use:
                 *
                 * response.setTransactionCode(
                 *     statusResponse.getTransactionCode()
                 * );
                 */

                finalizeSuccessfulPayment(
                        payment,
                        response,
                        statusResponse
                );

                break;

            case "CANCELED":
            case "NOT_FOUND":

                markPaymentFailedAndReleaseStock(
                        payment,
                        status
                );

                break;

            case "PENDING":
            case "AMBIGUOUS":
            case "AMBIGIOUS":

                /*
                 * Leave payment pending.
                 */
                payment.setProviderStatus(status);
                paymentRepository.save(payment);

                break;

            default:

                /*
                 * Unknown state.
                 * Leave it pending rather than releasing stock.
                 */
                payment.setProviderStatus(status);
                paymentRepository.save(payment);
        }
    }
}