package com.ravi.orbit.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
public class OrderProductRequestDTO {

    private UUID productId;

    private Map<UUID, Integer> variantQuantities;

}