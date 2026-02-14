package com.neuragate.config;

import com.neuragate.telemetry.GatewayTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Day 19: Kafka Consumer Configuration
 * 
 * Configures Kafka consumers for telemetry event processing.
 * 
 * Consumer groups:
 * - neuragate-analytics: Processes telemetry for real-time analytics
 * 
 * Deserialization:
 * - Keys: String (route ID, correlation ID)
 * - Values: JSON → GatewayTelemetry objects
 * 
 * Performance tuning:
 * - Auto-offset reset: earliest (process all historical data)
 * - Max poll records: 500 (batch size)
 * - Enable auto-commit: true (simplicity over exactly-once)
 */
@Slf4j
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Consumer factory for GatewayTelemetry objects.
     * 
     * Configures JSON deserialization with type mapping.
     * 
     * @return ConsumerFactory for telemetry events
     */
    @Bean
    public ConsumerFactory<String, GatewayTelemetry> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Connection
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Consumer group
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "neuragate-analytics");

        // Deserialization
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON deserializer configuration
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, GatewayTelemetry.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        // Offset management
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        config.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 5000);

        // Performance tuning
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        log.info("✅ Kafka consumer factory configured: {}", bootstrapServers);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Kafka listener container factory.
     * 
     * Manages consumer threads and message processing.
     * 
     * @return ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, GatewayTelemetry> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, GatewayTelemetry> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Concurrency: Number of consumer threads
        // Should match topic partition count for optimal throughput
        factory.setConcurrency(3);

        // Batch listener: Process messages in batches for higher throughput
        // Set to false for simplicity (process one message at a time)
        factory.setBatchListener(false);

        log.info("📥 Kafka listener container factory configured (concurrency: 3)");
        return factory;
    }
}
