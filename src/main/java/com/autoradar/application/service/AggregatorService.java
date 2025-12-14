package com.autoradar.application.service;

import com.autoradar.business.CarDataParser;
import com.autoradar.domain.dto.CarDTO;
import com.autoradar.domain.scraper.CarScraperStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class AggregatorService {

    private static final Logger logger = LoggerFactory.getLogger(AggregatorService.class);
    
    private final List<CarScraperStrategy> scrapers;
    private final CarDataParser parser;

    public AggregatorService(List<CarScraperStrategy> scrapers, CarDataParser parser) {
        this.scrapers = scrapers;
        this.parser = parser;
    }

    public List<CarDTO> searchAll(String query, String location, Double maxPrice) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            List<CarDTO> allCars = scrapers.stream()
                    .map(scraper -> executor.submit(() -> scraper.search(query, location)))
                    .flatMap(future -> {
                        try {
                            return future.get().stream();
                        } catch (Exception e) {
                            return List.<CarDTO>of().stream();
                        }
                    })
                    .collect(Collectors.toList());

            
            if (maxPrice != null) {
                allCars = allCars.stream()
                        .filter(car -> {
                            Double value = parser.parsePrice(car.price());
                            return value != null && value <= maxPrice;
                        })
                        .collect(Collectors.toList());
            }

            
            allCars.sort(Comparator.comparingDouble(car -> {
                Double value = parser.parsePrice(car.price());
                return Objects.requireNonNullElse(value, Double.MAX_VALUE);
            }));

            return allCars;
        }
    }
}