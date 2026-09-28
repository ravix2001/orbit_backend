package com.ravi.orbit.service.impl;

import com.ravi.orbit.dto.ProductDTO;
import com.ravi.orbit.dto.ProductVariantDTO;
import com.ravi.orbit.entity.Category;
import com.ravi.orbit.entity.Product;
import com.ravi.orbit.entity.ProductVariant;
import com.ravi.orbit.entity.User;
import com.ravi.orbit.enums.EStatus;
import com.ravi.orbit.exceptions.BadRequestException;
import com.ravi.orbit.repository.ProductRepository;
import com.ravi.orbit.repository.ProductVariantRepository;
import com.ravi.orbit.service.ICategoryService;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements IProductService {

    private final ICategoryService categoryService;
    private final IUserService userService;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    public void createProducts(List<ProductDTO> requests) {

        for (ProductDTO request : requests) {
            createProduct(request);
        }
    }

    @Override
    public ProductDTO createProduct(ProductDTO request) {

        User seller = userService.getUserPrincipal();

        Category category = categoryService.getCategoryById(request.getCategoryId());

        /*
         * PRODUCT
         */
        Product product = new Product();

        product.setSeller(seller);

        product.setCategory(category);

        if (!CommonMethods.isEmpty(request.getCode())) {
            product.setCode(request.getCode());
        } else {
            product.setCode(generateProductCode());
        }

        product.setName(request.getName());

        product.setBrand(request.getBrand());

        product.setFeatures(request.getFeatures());

        product.setDescription(request.getDescription());

        product.setStatus(EStatus.ACTIVE);

        product.setImageUrl(request.getImageUrl());

        BigDecimal marketPrice = request.getMarketPrice();
        BigDecimal sellingPrice = request.getSellingPrice();

        BigDecimal discountPercent = request.getDiscountPercent();
        BigDecimal discountAmount = request.getDiscountAmount();

        /*
         * CASE 1:
         * DISCOUNT PERCENT PROVIDED
         */
        if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {

            discountAmount = marketPrice.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            sellingPrice = marketPrice.subtract(discountAmount);
        }

        /*
         * CASE 2:
         * DISCOUNT AMOUNT PROVIDED
         */
        else if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {

            discountPercent = discountAmount.multiply(BigDecimal.valueOf(100)).divide(marketPrice, 2, RoundingMode.HALF_UP);

            sellingPrice = marketPrice.subtract(discountAmount);
        }

        /*
         * SET PRICES
         */
        product.setMarketPrice(marketPrice);

        product.setSellingPrice(sellingPrice);

        product.setDiscountPercent(discountPercent);

        product.setDiscountAmount(discountAmount);

        Integer totalQuantity = 0;

        if (!CommonMethods.isEmpty(request.getVariants())) {
            totalQuantity = request.getVariants()
                    .stream()
                    .mapToInt(ProductVariantDTO::getQuantity)
                    .sum();
        } else {
            totalQuantity = request.getQuantity();
        }

        product.setQuantity(totalQuantity);

        /*
         * SAVE PRODUCT FIRST
         */
        Product savedProduct = productRepository.save(product);

        /*
         * VARIANTS
         */
        List<ProductVariant> variants = new ArrayList<>();

        /*
         * CASE 1:
         * PRODUCT HAS VARIANTS
         */
        if (!CommonMethods.isEmpty(request.getVariants())) {

            for (ProductVariantDTO variantDTO : request.getVariants()) {

                ProductVariant variant = new ProductVariant();

                variant.setProduct(savedProduct);

                variant.setColor(variantDTO.getColor());

                variant.setSize(variantDTO.getSize());

                variant.setQuantity(variantDTO.getQuantity());

                variant.setIsAvailable(variantDTO.getQuantity() > 0);

                variant.setAdditionalPrice(variantDTO.getAdditionalPrice());

                variant.setSku(generateSku(savedProduct.getCode(), variantDTO.getColor(), variantDTO.getSize()));

                variants.add(variant);

                variantDTO.setId(variant.getId());
                variantDTO.setSku(variant.getSku());
            }

        }

        /*
         * CASE 2:
         * NO VARIANTS
         * CREATE DEFAULT VARIANT
         */
        else {

            ProductVariant defaultVariant = new ProductVariant();

            defaultVariant.setProduct(savedProduct);

            defaultVariant.setColor(null);

            defaultVariant.setSize(null);

            defaultVariant.setQuantity(request.getQuantity());

            defaultVariant.setIsAvailable(request.getQuantity() > 0);

            defaultVariant.setAdditionalPrice(BigDecimal.ZERO);

            defaultVariant.setSku(generateDefaultSku(savedProduct.getCode()));

            variants.add(defaultVariant);
        }

        /*
         * SAVE ALL VARIANTS
         */
        productVariantRepository.saveAll(variants);

        request.setId(savedProduct.getId());
        request.setCode(savedProduct.getCode());
        request.setSellingPrice(savedProduct.getSellingPrice());
        request.setStatus(savedProduct.getStatus());
        request.setQuantity(savedProduct.getQuantity());

        return request;
    }

//    @Caching(evict = {
//            @CacheEvict(value = "products", key = "#request.id"),
////            @CacheEvict(value = "product-pages", allEntries = true)
//    })
    @Override
    public ProductDTO updateProduct(ProductDTO request) {

        Product product = getProductById(request.getId());

        Category category = categoryService.getCategoryById(request.getCategoryId());

        /*
         * UPDATE PRODUCT
         */
        product.setCategory(category);
        product.setName(request.getName());
        product.setCode(request.getCode());
        product.setBrand(request.getBrand());
        product.setFeatures(request.getFeatures());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());

        BigDecimal marketPrice = request.getMarketPrice();
        BigDecimal sellingPrice = request.getSellingPrice();

        BigDecimal discountPercent = request.getDiscountPercent();
        BigDecimal discountAmount = request.getDiscountAmount();

        if (discountPercent != null
                && discountPercent.compareTo(BigDecimal.ZERO) > 0) {

            discountAmount = marketPrice
                    .multiply(discountPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            sellingPrice = marketPrice.subtract(discountAmount);

        } else if (discountAmount != null
                && discountAmount.compareTo(BigDecimal.ZERO) > 0) {

            discountPercent = discountAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(marketPrice, 2, RoundingMode.HALF_UP);

            sellingPrice = marketPrice.subtract(discountAmount);
        }

        product.setMarketPrice(marketPrice);
        product.setSellingPrice(sellingPrice);
        product.setDiscountPercent(discountPercent);
        product.setDiscountAmount(discountAmount);

        /*
         * EXISTING VARIANTS
         */
        List<ProductVariant> existingVariants = getProductVariantsByProductId(product.getId());

        Map<UUID, ProductVariant> existingVariantMap =
                existingVariants.stream()
                        .collect(Collectors.toMap(ProductVariant::getId,
                                Function.identity()));

        List<ProductVariant> variantsToSave = new ArrayList<>();

        Set<UUID> requestVariantIds = new HashSet<>();

        int totalQuantity = 0;

        for (ProductVariantDTO variantDTO : request.getVariants()) {

            ProductVariant variant;

            /*
             * UPDATE EXISTING VARIANT
             */
            if (!CommonMethods.isEmpty(variantDTO.getId())) {

                variant = existingVariantMap.get(variantDTO.getId());

                if (variant == null) {
                    throw new BadRequestException(MyConstants.ERR_MSG_NOT_FOUND + "Variant: " + variantDTO.getId());
                }

                requestVariantIds.add(variantDTO.getId());

            }
            /*
             * NEW VARIANT
             */
            else {

                variant = new ProductVariant();

                variant.setProduct(product);

                variant.setSku(generateSku(
                        product.getCode(),
                        variantDTO.getColor(),
                        variantDTO.getSize()));
            }

            variant.setColor(variantDTO.getColor());
            variant.setSize(variantDTO.getSize());
            variant.setQuantity(variantDTO.getQuantity());
            variant.setAdditionalPrice(variantDTO.getAdditionalPrice());
            variant.setIsAvailable(variantDTO.getQuantity() > 0);

            totalQuantity += variantDTO.getQuantity();

            variantsToSave.add(variant);
        }

        /*
         * DELETE REMOVED VARIANTS
         */
        List<ProductVariant> variantsToDelete =
                existingVariants.stream()
                        .filter(v -> !requestVariantIds.contains(v.getId()))
                        .toList();

        if (!CommonMethods.isEmpty(variantsToDelete)) {
            productVariantRepository.deleteAll(variantsToDelete);
        }

        /*
         * SAVE UPDATED/NEW VARIANTS
         */
        productVariantRepository.saveAll(variantsToSave);

        product.setQuantity(totalQuantity);

        Product updatedProduct = productRepository.save(product);

        request.setCode(updatedProduct.getCode());
        request.setSellingPrice(updatedProduct.getSellingPrice());
        request.setQuantity(updatedProduct.getQuantity());

        return request;
    }


//    @Cacheable(
//            value = "product-pages",
//            key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort"
//    )
    @Override
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        return productRepository.getAllProducts(pageable);
    }

//    @Cacheable(value = "products", key = "#productId")
    @Override
    public ProductDTO getProduct(UUID productId) {

        ProductDTO productDTO = getProductDTOById(productId);

        List<ProductVariantDTO> variants = getProductVariantsDTOByProductId(productId);

        productDTO.setVariants(variants);

        int totalQuantity = variants.stream()
                .map(ProductVariantDTO::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        productDTO.setQuantity(totalQuantity);

        return productDTO;
    }

    @Override
    public ProductDTO getProductByCode(String code) {

        ProductDTO productDTO = getProductDTOByCode(code);

        List<ProductVariantDTO> variants = getProductVariantsDTOByProductId(productDTO.getId());

        productDTO.setVariants(variants);

        int totalQuantity = variants.stream()
                .map(ProductVariantDTO::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        productDTO.setQuantity(totalQuantity);

        return productDTO;
    }


    @Override
    public Page<ProductDTO> getProductDTOsByName(Pageable pageable, String name) {
        return productRepository.getProductDTOsByName(pageable, name);
    }

    @Override
    public Page<ProductDTO> getProductDTOsByCategoryId(Pageable pageable, UUID categoryId) {
        return productRepository.getProductDTOsByCategoryId(pageable, categoryId);
    }

    @Override
    public Page<ProductDTO> getProductDTOsBySellerId(Pageable pageable, UUID sellerId) {
        return productRepository.getProductDTOsBySellerId(pageable, sellerId);
    }

    public ProductDTO getProductDTOById(UUID id) {
        return productRepository.getProductDTOById(id)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "Product: " + id));
    }

    public ProductDTO getProductDTOByCode(String code) {
        return productRepository.getProductDTOByCode(code)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "Product: " + code));
    }

//    @Caching(evict = {
//            @CacheEvict(value = "products", key = "#id"),
////            @CacheEvict(value = "product-pages", allEntries = true)
//    })
    @Override
    public void deleteProduct(UUID id) {
        Product product = getProductById(id);
        product.setStatus(EStatus.DELETED);
        productRepository.save(product);
    }

//    @Caching(evict = {
//            @CacheEvict(value = "products", key = "#id"),
////            @CacheEvict(value = "product-pages", allEntries = true)
//    })
    @Override
    public void deleteProductHard(UUID id) {   // remaining to delete its children
        Product product = getProductById(id);
        productRepository.delete(product);
    }

    @Override
    public Product getProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "Product: " + id));
    }

    public List<ProductVariantDTO> getProductVariantsDTOByProductId(UUID productId) {
        return productVariantRepository.getProductVariantsByProductId(productId);
    }

    public List<ProductVariant> getProductVariantsByProductId(UUID productId) {
        return productVariantRepository.findAllByProductId(productId);
    }

    @Override
    public ProductVariant getProductVariantById(UUID variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "Product Variant: " + variantId));
    }

    @Override
    public ProductVariantDTO getProductVariantDTOById(UUID variantId) {
        return productVariantRepository.getProductVariantById(variantId)
                .orElseThrow(() -> new BadRequestException(MyConstants
                        .ERR_MSG_NOT_FOUND + "Product Variant: " + variantId));
    }

    private String generateProductCode() {
        return "PRD-" + System.currentTimeMillis();
    }

    private String generateSku(String productCode, String color, String size) {
        String colorPart = color != null ? color.toUpperCase() : "DEFAULT";
        String sizePart = size != null ? size.toUpperCase() : "STD";
        return productCode + "-" + colorPart + "-" + sizePart;
    }

    private String generateDefaultSku(String productCode) {
        return productCode + "-DEFAULT";
    }

}