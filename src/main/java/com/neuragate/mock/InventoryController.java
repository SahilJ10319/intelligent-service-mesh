package com.neuragate.mock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * Day 11: Inventory Mock Service Controller
 * 
 * Simulates a real inventory service for testing the gateway.
 * This mock service demonstrates:
 * - Dynamic routing through the gateway
 * - Circuit breaker and retry behavior
 * - Health monitoring integration
 * - Reactive endpoints
 * 
 * Runs on port 9001 (separate from gateway on 8080)
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final Random random = new Random();

    /**
     * Get all inventory items.
     * 
     * @return Flux of all products
     */
    @GetMapping
    public Flux<Product> getAllInventory() {
        log.info("Fetching all inventory items");

        List<Product> products = List.of(
                new Product(1L, "Laptop Pro 16", 2499.99, 15, "Electronics"),
                new Product(2L, "Wireless Mouse", 29.99, 150, "Accessories"),
                new Product(3L, "USB-C Cable", 12.99, 300, "Accessories"),
                new Product(4L, "4K Monitor", 599.99, 25, "Electronics"),
                new Product(5L, "Mechanical Keyboard", 149.99, 45, "Accessories"),
                new Product(6L, "Webcam HD", 89.99, 60, "Electronics"),
                new Product(7L, "Desk Lamp", 39.99, 80, "Office"),
                new Product(8L, "Ergonomic Chair", 399.99, 12, "Office"),
                new Product(9L, "Standing Desk", 799.99, 8, "Office"),
                new Product(10L, "Noise Cancelling Headphones", 299.99, 35, "Electronics"));

        return Flux.fromIterable(products);
    }

    /**
     * Get a specific product by ID.
     * 
     * @param id Product ID
     * @return Mono of product or empty if not found
     */
    @GetMapping("/{id}")
    public Mono<Product> getProductById(@PathVariable Long id) {
        log.info("Fetching product with id: {}", id);

        return getAllInventory()
                .filter(p -> p.getId().equals(id))
                .next()
                .doOnSuccess(product -> {
                    if (product != null) {
                        log.info("Found product: {}", product.getName());
                    } else {
                        log.warn("Product not found with id: {}", id);
                    }
                });
    }

    /**
     * Get products by category.
     * 
     * @param category Product category
     * @return Flux of products in category
     */
    @GetMapping("/category/{category}")
    public Flux<Product> getProductsByCategory(@PathVariable String category) {
        log.info("Fetching products in category: {}", category);

        return getAllInventory()
                .filter(p -> p.getCategory().equalsIgnoreCase(category));
    }

    /**
     * Check stock availability for a product.
     * 
     * @param id Product ID
     * @return Mono of stock status
     */
    @GetMapping("/{id}/stock")
    public Mono<StockStatus> checkStock(@PathVariable Long id) {
        log.info("Checking stock for product: {}", id);

        return getProductById(id)
                .map(product -> {
                    String status;
                    if (product.getStock() > 50) {
                        status = "IN_STOCK";
                    } else if (product.getStock() > 0) {
                        status = "LOW_STOCK";
                    } else {
                        status = "OUT_OF_STOCK";
                    }

                    return new StockStatus(
                            product.getId(),
                            product.getName(),
                            product.getStock(),
                            status);
                });
    }

    /**
     * Simulate slow response for testing retry patterns.
     * 
     * @param delayMs Delay in milliseconds
     * @return Flux of products after delay
     */
    @GetMapping("/slow")
    public Flux<Product> getInventorySlow(@RequestParam(defaultValue = "3000") int delayMs) {
        log.warn("Slow endpoint called with {}ms delay", delayMs);

        return getAllInventory()
                .delayElements(Duration.ofMillis(delayMs / 10));
    }

    /**
     * Simulate random failures for testing circuit breaker.
     * 
     * @param failureRate Failure rate (0-100)
     * @return Flux of products or error
     */
    @GetMapping("/flaky")
    public Flux<Product> getFlakyInventory(@RequestParam(defaultValue = "50") int failureRate) {
        log.warn("Flaky endpoint called with {}% failure rate", failureRate);

        if (random.nextInt(100) < failureRate) {
            log.error("Simulating service failure");
            return Flux.error(new RuntimeException("Simulated service failure"));
        }

        return getAllInventory();
    }

    /**
     * Health check endpoint for this mock service.
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public Mono<HealthStatus> health() {
        return Mono.just(new HealthStatus("UP", "Inventory service is healthy"));
    }

    // DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private Long id;
        private String name;
        private Double price;
        private Integer stock;
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockStatus {
        private Long productId;
        private String productName;
        private Integer availableStock;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthStatus {
        private String status;
        private String message;
    }
}
