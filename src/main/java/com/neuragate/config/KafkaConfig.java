package com.neuragate.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Day 15: Kafka Configuration
 * 
 * Configures Kafka infrastructure for event-driven telemetry.
 * 
 * Architecture:
 * - Gateway events → Kafka topics → Analytics/Monitoring systems
 * - Decouples telemetry from request processing
 * - Enables real-time and batch analytics
 * - Provides audit trail and debugging capabilities
 * 
 * Topics:
 * - gateway-telemetry: Request/response metrics, latency, status codes
 * - gateway-errors: Error events, exceptions, circuit breaker state changes
 * - gateway-routes: Route creation, updates, deletions
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Day 15: Gateway Telemetry Topic
     * 
     * Stores all gateway request/response telemetry data.
     * 
     * Configuration:
     * - 3 partitions: Allows parallel processing by multiple consumers
     * - Replication factor 1: Single broker setup (increase for production)
     * - Retention: 7 days (configured in Kafka broker)
     * 
     * Events include:
     * - Request timestamp, method, path
     * - Response status code, latency
     * - Route ID, filters applied
     * - Client IP, user agent
     * 
     * @return NewTopic for gateway telemetry
     */
    @Bean
    public NewTopic gatewayTelemetryTopic() {
        log.info("📊 Creating Kafka topic: gateway-telemetry (3 partitions, replication factor 1)");
        return TopicBuilder.name("gateway-telemetry")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "604800000") // 7 days
                .config("compression.type", "gzip")
                .build();
    }

    /**
     * Day 15: Gateway Errors Topic
     * 
     * Stores error events and exceptions for monitoring and alerting.
     * 
     * Configuration:
     * - 2 partitions: Lower volume than telemetry
     * - Longer retention: 30 days for debugging
     * 
     * Events include:
     * - Exception type and message
     * - Stack trace
     * - Request context
     * - Circuit breaker state changes
     * - Rate limit violations
     * 
     * @return NewTopic for gateway errors
     */
    @Bean
    public NewTopic gatewayErrorsTopic() {
        log.info("🚨 Creating Kafka topic: gateway-errors (2 partitions, replication factor 1)");
        return TopicBuilder.name("gateway-errors")
                .partitions(2)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 days
                .config("compression.type", "gzip")
                .build();
    }

    /**
     * Day 15: Gateway Routes Topic
     * 
     * Stores route lifecycle events (create, update, delete).
     * 
     * Configuration:
     * - 1 partition: Low volume, order matters
     * - Compaction: Keep only latest state per route ID
     * 
     * Events include:
     * - Route ID, URI, predicates, filters
     * - Operation type (CREATE, UPDATE, DELETE)
     * - Timestamp, user/system that made change
     * 
     * @return NewTopic for gateway routes
     */
    @Bean
    public NewTopic gatewayRoutesTopic() {
        log.info("🛣️  Creating Kafka topic: gateway-routes (1 partition, replication factor 1)");
        return TopicBuilder.name("gateway-routes")
                .partitions(1)
                .replicas(1)
                .config("cleanup.policy", "compact")
                .config("retention.ms", "2592000000") // 30 days
                .build();
    }

    /**
     * Producer factory for sending JSON messages to Kafka.
     * 
     * Configuration:
     * - String keys: Route ID, request ID, etc.
     * - JSON values: Event payloads
     * - Idempotence enabled: Prevents duplicate messages
     * - Compression: GZIP for network efficiency
     * 
     * @return ProducerFactory configured for JSON serialization
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Connection
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serialization
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader acknowledgment
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Performance
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        // JSON serializer configuration
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        log.info("✅ Kafka producer factory configured: {}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate for sending messages to Kafka topics.
     * 
     * This is the main interface for producing messages.
     * Supports both synchronous and asynchronous sends.
     * 
     * @return KafkaTemplate configured with JSON serialization
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        log.info("📤 Creating KafkaTemplate for telemetry events");
        return new KafkaTemplate<>(producerFactory());
    }
}
