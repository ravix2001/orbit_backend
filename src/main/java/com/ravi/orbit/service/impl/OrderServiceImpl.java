package com.ravi.orbit.service.impl;

import com.ravi.orbit.dto.OrderDTO;
import com.ravi.orbit.dto.OrderItemDTO;
import com.ravi.orbit.dto.OrderProductRequestDTO;
import com.ravi.orbit.dto.ProductVariantDTO;
import com.ravi.orbit.entity.Order;
import com.ravi.orbit.entity.OrderItem;
import com.ravi.orbit.entity.Product;
import com.ravi.orbit.entity.ProductVariant;
import com.ravi.orbit.entity.User;
import com.ravi.orbit.enums.EOrderPaymentStatus;
import com.ravi.orbit.enums.EOrderStatus;
import com.ravi.orbit.exceptions.BadRequestException;
import com.ravi.orbit.repository.OrderItemRepository;
import com.ravi.orbit.repository.OrderRepository;
import com.ravi.orbit.repository.ProductRepository;
import com.ravi.orbit.repository.ProductVariantRepository;
import com.ravi.orbit.service.IOrderService;
import com.ravi.orbit.service.IProductService;
import com.ravi.orbit.service.IUserService;
import com.ravi.orbit.utils.CommonMethods;
import com.ravi.orbit.utils.MyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements IOrderService {

    private final IUserService userService;
    private final IProductService productService;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

//    @Caching(evict = {
//            @CacheEvict(value = "products", allEntries = true)
//    })
    @Override
    public OrderDTO createOrder(OrderDTO request) {

        User customer = userService.getUserPrincipal();

        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new BadRequestException("Order must contain at least one product.");
        }

        /*
         * CREATE ORDER
         */
        Order order = new Order();

        order.setCustomer(customer);

        order.setCustomerName(CommonMethods.getName(
                customer.getFirstName(),
                customer.getMiddleName(),
                customer.getLastName()
        ));

        order.setCustomerPhone(customer.getPhone());

        order.setCustomerImage(customer.getImageUrl());

        order.setOrderNumber(generateOrderNumber());

        BigDecimal totalMarketPrice = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        int totalItems = 0;

        List<OrderItem> orderItems = new ArrayList<>();

        /*
         * PROCESS ALL PRODUCTS IN THE CART
         */
        for (OrderProductRequestDTO productRequest : request.getProducts()) {

            if (productRequest.getProductId() == null) {
                throw new BadRequestException("Product ID is required.");
            }

            if (productRequest.getVariantQuantities() == null
                    || productRequest.getVariantQuantities().isEmpty()) {
                throw new BadRequestException(
                        "Variant quantities are required for product: "
                                + productRequest.getProductId()
                );
            }

            /*
             * FETCH PRODUCT
             */
            Product product =
                    productService.getProductById(productRequest.getProductId());

            /*
             * FETCH VARIANTS
             */
            Set<UUID> variantIds =
                    productRequest.getVariantQuantities().keySet();

            List<ProductVariant> variants =
                    productVariantRepository.findByVariantIds(variantIds);

            /*
             * VALIDATE VARIANTS
             */
            if (variants == null || variants.isEmpty()) {
                throw new BadRequestException(
                        MyConstants.ERR_MSG_NOT_FOUND + "Variants"
                );
            }

            Map<UUID, Integer> quantityMap =
                    productRequest.getVariantQuantities();

            /*
             * CREATE ORDER ITEMS
             */
            for (ProductVariant variant : variants) {

                Integer requestedQuantity =
                        quantityMap.get(variant.getId());

                if (requestedQuantity == null || requestedQuantity <= 0) {
                    throw new BadRequestException(
                            "Invalid quantity for variant: " + variant.getId()
                    );
                }

                int quantity = requestedQuantity;

                /*
                 * VALIDATE STOCK
                 */
                if (!Boolean.TRUE.equals(variant.getIsAvailable())) {
                    throw new BadRequestException(
                            product.getName() + " is unavailable."
                    );
                }

                if (variant.getQuantity() < quantity) {
                    throw new BadRequestException(
                            "Insufficient stock for " + product.getName()
                    );
                }

                /*
                 * PRICE CALCULATION
                 */
                BigDecimal basePrice =
                        product.getSellingPrice() != null
                                ? product.getSellingPrice()
                                : BigDecimal.ZERO;

                BigDecimal extraPrice =
                        variant.getAdditionalPrice() != null
                                ? variant.getAdditionalPrice()
                                : BigDecimal.ZERO;

                BigDecimal sellingPrice =
                        basePrice.add(extraPrice);

                BigDecimal marketPrice =
                        product.getMarketPrice() != null
                                ? product.getMarketPrice()
                                : BigDecimal.ZERO;

                BigDecimal itemTotal =
                        sellingPrice.multiply(
                                BigDecimal.valueOf(quantity)
                        );

                BigDecimal itemMarketTotal =
                        marketPrice.multiply(
                                BigDecimal.valueOf(quantity)
                        );

                BigDecimal itemDiscount =
                        itemMarketTotal.subtract(itemTotal);

                /*
                 * CREATE ORDER ITEM
                 */
                OrderItem orderItem = new OrderItem();

                orderItem.setOrder(order);

                orderItem.setProductId(product.getId());

                orderItem.setProductName(product.getName());

                orderItem.setProductImage(product.getImageUrl());

                orderItem.setVariantId(variant.getId());

                orderItem.setSeller(product.getSeller());

                orderItem.setSellerName(
                        CommonMethods.getName(
                                product.getSeller().getFirstName(),
                                product.getSeller().getMiddleName(),
                                product.getSeller().getLastName()
                        )
                );

                orderItem.setSellerPhone(
                        product.getSeller().getPhone()
                );

                orderItem.setSellerImage(
                        product.getSeller().getImageUrl()
                );

                orderItem.setQuantity(quantity);

                orderItem.setMarketPrice(marketPrice);

                orderItem.setSellingPrice(sellingPrice);

                orderItem.setDiscountAmount(itemDiscount);

                orderItem.setTotalAmount(itemTotal);

                orderItems.add(orderItem);

                /*
                 * RESERVE / DEDUCT STOCK
                 */
                variant.setQuantity(
                        variant.getQuantity() - quantity
                );

                product.setQuantity(
                        product.getQuantity() - quantity
                );

                if (variant.getQuantity() <= 0) {
                    variant.setIsAvailable(false);
                }

                productVariantRepository.save(variant);

                productRepository.save(product);

                /*
                 * ORDER TOTALS
                 */
                totalItems += quantity;

                totalMarketPrice =
                        totalMarketPrice.add(itemMarketTotal);

                totalDiscount =
                        totalDiscount.add(itemDiscount);

                totalAmount =
                        totalAmount.add(itemTotal);
            }
        }

        /*
         * SET ORDER TOTALS
         */
        order.setTotalItems(totalItems);

        order.setTotalMarketPrice(totalMarketPrice);

        order.setTotalDiscount(totalDiscount);

        order.setTotalAmount(totalAmount);

        /*
         * INITIAL PAYMENT / ORDER STATE
         */
        order.setOrderStatus(EOrderStatus.PENDING);

        order.setPaymentStatus(EOrderPaymentStatus.UNPAID);

        /*
         * SAVE ORDER
         */
        Order savedOrder = orderRepository.save(order);

        /*
         * SAVE ORDER ITEMS
         */
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(savedOrder);
        }

        orderItemRepository.saveAll(orderItems);

        /*
         * RETURN RESPONSE
         */
        return convertToDTO(savedOrder);
    }

    public Long generateOrderNumber() {
        Long maxOrderNumber = orderRepository.findMaxOrderNumber();

        if (maxOrderNumber == null) {
            return 1000L;
        } else {
            return maxOrderNumber + 1;
        }
    }

    @Override
    public OrderDTO getOrder(UUID id) {

        OrderDTO orderDTO = getOrderDTOById(id);

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId((orderDTO.getId()));

        List<OrderItemDTO> orderItemDTOs = orderItems
                .stream()
                .map(this::convertItemToDTO)
                .toList();

        orderDTO.setOrderItems(orderItemDTOs);

        return orderDTO;
    }

    @Override
    public OrderDTO getOrderByOrderNumber(Long orderNumber) {
        OrderDTO orderDTO = orderRepository.getOrderByOrderNumber(orderNumber)
                .orElseThrow(() -> new BadRequestException(MyConstants.ERR_MSG_NOT_FOUND + "Order: " + orderNumber));

//        orderDTO.setCustomer(userService.getUserDTOById(orderDTO.getCustomerId()));
//        orderDTO.setSeller(userService.getUserDTOById(orderDTO.getSellerId()));
//        orderDTO.setProduct(productService.getProduct(orderDTO.getProductId()));

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId((orderDTO.getId()));

        List<OrderItemDTO> orderItemDTOs = orderItems
                .stream()
                .map(this::convertItemToDTO)
                .toList();

        orderDTO.setOrderItems(orderItemDTOs);

        return orderDTO;
    }

    @Override
    public Page<OrderDTO> getOrdersByCustomerId(UUID customerId, Pageable pageable){
        return orderRepository.getOrdersByCustomerId(customerId, pageable);
    }

    @Override
    public Page<OrderDTO> getOrdersBySellerId(UUID sellerId, Pageable pageable){
        return orderRepository.getOrdersBySellerId(sellerId, pageable);
    }

    @Override
    public void confirmOrder(UUID orderId) {

        Order order = getOrderById(orderId);

        if (order.getOrderStatus() == EOrderStatus.CONFIRMED) {
            throw new BadRequestException("Order already confirmed");
        }

        if (order.getOrderStatus() != EOrderStatus.PLACED) {
            throw new BadRequestException("Only placed order can be confirmed");
        }

        order.setOrderStatus(EOrderStatus.CONFIRMED);

        orderRepository.save(order);
    }

    // todo: cancelling the order updates the quantity in the stock
    // from  seller side
    @Override
    public void cancelOrder(UUID orderId) {

        Order order = getOrderById(orderId);

        if (order.getOrderStatus() == EOrderStatus.CANCELLED) {
            throw new BadRequestException("Order already cancelled");
        }

        if (order.getOrderStatus() != EOrderStatus.PLACED) {
            throw new BadRequestException("Only placed order can be cancelled");
        }

        order.setOrderStatus(EOrderStatus.CANCELLED);

        orderRepository.save(order);
    }

    @Override
    public OrderDTO getOrderDTOById(UUID id){
        return orderRepository.getOrderById(id)
                .orElseThrow(() -> new BadRequestException(MyConstants.ERR_MSG_NOT_FOUND + "Order: " + id));
    }

    @Override
    public Order getOrderById(UUID id){
        return orderRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(MyConstants.ERR_MSG_NOT_FOUND + "Order: " + id));
    }

    private OrderDTO convertToDTO(Order order) {

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId((order.getId()));

        List<OrderItemDTO> orderItemDTOs = orderItems
                .stream()
                .map(this::convertItemToDTO)
                .collect(toList());

        OrderDTO dto = new OrderDTO();

        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setPaymentStatus(order.getPaymentStatus());

        // Customer info
        dto.setCustomerName(order.getCustomerName());
        dto.setCustomerPhone(order.getCustomerPhone());
        dto.setCustomerImage(order.getCustomerImage());

        // Totals
        dto.setTotalItems(order.getTotalItems());
        dto.setTotalMarketPrice(order.getTotalMarketPrice());
        dto.setTotalDiscount(order.getTotalDiscount());
        dto.setTotalAmount(order.getTotalAmount());

        dto.setOrderDate(order.getOrderDate().toLocalDate());
        dto.setDeliveryDate(order.getDeliveryDate().toLocalDate());
        dto.setOrderItems(orderItemDTOs);

        return dto;
    }

    private OrderItemDTO convertItemToDTO(OrderItem item) {

        User seller = item.getSeller();

        ProductVariantDTO variantDTO = productService.getProductVariantDTOById(item.getVariantId());

        OrderItemDTO dto = new OrderItemDTO();

        dto.setId(item.getId());

        // Product info
        dto.setProductName(item.getProductName());
        dto.setProductImage(item.getProductImage());
        dto.setVariant(variantDTO);

        dto.setQuantity(item.getQuantity());

        // Pricing
        dto.setMarketPrice(item.getMarketPrice());
        dto.setSellingPrice(item.getSellingPrice());
        dto.setDiscountAmount(item.getDiscountAmount());
        dto.setTotalAmount(item.getTotalAmount());

        // Seller info
        dto.setSellerId(seller.getId());
        dto.setSellerName(CommonMethods.getName(
                seller.getFirstName(), seller.getMiddleName(), seller.getLastName()));
        dto.setSellerImage(seller.getImageUrl());

        return dto;
    }

}
