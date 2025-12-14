
package com.autoradar.domain.dto;

public record CarDTO(
        String title,
        String price,
        Integer year,
        Integer km,
        String originalLink,
        String source,
        String imageUrl
) {}
