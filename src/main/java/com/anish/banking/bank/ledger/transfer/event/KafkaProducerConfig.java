package com.anish.banking.bank.ledger.transfer.event;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

// Hand-wired instead of relying on Spring Boot autoconfiguration: this Boot version has no
// Kafka autoconfiguration module (spring-kafka only provides @EnableKafka's listener
// machinery — no ProducerFactory/KafkaTemplate bean shows up from spring.kafka.* properties
// alone), so the producer factory is built explicitly here. Only bootstrap-servers is
// externalized (application.properties); the rest are fixed choices, not per-environment knobs.
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                // Fail fast (default is 60s) if the broker is unreachable, so a Kafka outage
                // can't stall the transfer response — publishing happens after the DB commit
                // and must stay best-effort (see TransferEventPublisher).
                ProducerConfig.MAX_BLOCK_MS_CONFIG, "3000");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
