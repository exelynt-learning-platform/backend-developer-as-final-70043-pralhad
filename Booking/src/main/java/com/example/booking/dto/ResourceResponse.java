package com.example.booking.dto;



import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceResponse {
    private Long id;
    private String name;
    private String description;
    private String type;
    private boolean available;
    private BigDecimal price;
}