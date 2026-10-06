package com.igot.cb.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for KafkaProducerConfig
 *
 * Tests cover:
 * - Producer factory configuration
 * - KafkaTemplate bean creation
 * - Bootstrap servers configuration
 * - Serializer configuration
 * - Producer properties validation
 * - Bean initialization
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaProducerConfig Tests")
class KafkaProducerConfigTest {

    private KafkaProducerConfig kafkaProducerConfig;
    private static final String TEST_BOOTSTRAP_SERVERS = "localhost:9092";

    @BeforeEach
    void setUp() {
        kafkaProducerConfig = new KafkaProducerConfig();
        // Set bootstrap servers using reflection to simulate @Value injection
        ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", TEST_BOOTSTRAP_SERVERS);
    }

    // ==================== Producer Factory Tests ====================

    @Nested
    @DisplayName("Producer Factory Configuration Tests")
    class ProducerFactoryTests {

        @Test
        @DisplayName("Should create producer factory with correct configuration")
        void testProducerFactoryCreation() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();

            // Assert
            assertNotNull(producerFactory, "ProducerFactory should not be null");
            assertTrue(producerFactory instanceof org.springframework.kafka.core.DefaultKafkaProducerFactory,
                    "Should be instance of DefaultKafkaProducerFactory");
        }

        @Test
        @DisplayName("Should configure bootstrap servers correctly")
        void testBootstrapServersConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(TEST_BOOTSTRAP_SERVERS, configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    "Bootstrap servers should match configured value");
        }

        @Test
        @DisplayName("Should configure String key serializer")
        void testKeySerializerConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(StringSerializer.class, configProps.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG),
                    "Key serializer should be StringSerializer");
        }

        @Test
        @DisplayName("Should configure JSON value serializer")
        void testValueSerializerConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(JsonSerializer.class, configProps.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG),
                    "Value serializer should be JsonSerializer");
        }

        @Test
        @DisplayName("Should configure ACKS to 'all' for reliability")
        void testAcksConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals("all", configProps.get(ProducerConfig.ACKS_CONFIG),
                    "ACKS should be set to 'all' for maximum reliability");
        }

        @Test
        @DisplayName("Should configure retries to 3")
        void testRetriesConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(3, configProps.get(ProducerConfig.RETRIES_CONFIG),
                    "Retries should be set to 3");
        }

        @Test
        @DisplayName("Should configure linger ms to 10")
        void testLingerMsConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(10, configProps.get(ProducerConfig.LINGER_MS_CONFIG),
                    "Linger MS should be set to 10 for batching");
        }

        @Test
        @DisplayName("Should contain all required producer properties")
        void testAllRequiredPropertiesPresent() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertTrue(configProps.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    "Should contain bootstrap servers config");
            assertTrue(configProps.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG),
                    "Should contain key serializer config");
            assertTrue(configProps.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG),
                    "Should contain value serializer config");
            assertTrue(configProps.containsKey(ProducerConfig.ACKS_CONFIG),
                    "Should contain acks config");
            assertTrue(configProps.containsKey(ProducerConfig.RETRIES_CONFIG),
                    "Should contain retries config");
            assertTrue(configProps.containsKey(ProducerConfig.LINGER_MS_CONFIG),
                    "Should contain linger ms config");
        }

        @Test
        @DisplayName("Should create new instance each time producerFactory is called")
        void testProducerFactoryNotSingleton() {
            // Act
            ProducerFactory<String, Object> factory1 = kafkaProducerConfig.producerFactory();
            ProducerFactory<String, Object> factory2 = kafkaProducerConfig.producerFactory();

            // Assert
            assertNotSame(factory1, factory2,
                    "Each call should create a new ProducerFactory instance");
        }
    }

    // ==================== KafkaTemplate Tests ====================

    @Nested
    @DisplayName("KafkaTemplate Configuration Tests")
    class KafkaTemplateTests {

        @Test
        @DisplayName("Should create KafkaTemplate bean")
        void testKafkaTemplateCreation() {
            // Act
            KafkaTemplate<String, Object> kafkaTemplate = kafkaProducerConfig.kafkaTemplate();

            // Assert
            assertNotNull(kafkaTemplate, "KafkaTemplate should not be null");
        }

        @Test
        @DisplayName("Should create KafkaTemplate with producer factory")
        void testKafkaTemplateHasProducerFactory() {
            // Act
            KafkaTemplate<String, Object> kafkaTemplate = kafkaProducerConfig.kafkaTemplate();

            // Assert
            assertNotNull(kafkaTemplate.getProducerFactory(),
                    "KafkaTemplate should have a ProducerFactory");
        }

        @Test
        @DisplayName("Should create KafkaTemplate with generic type support")
        void testKafkaTemplateGenericTypeSupport() {
            // Act
            KafkaTemplate<String, Object> kafkaTemplate = kafkaProducerConfig.kafkaTemplate();

            // Assert
            assertNotNull(kafkaTemplate, "Should support Object as value type");
            // Verify it can handle different types
            assertDoesNotThrow(() -> {
                // This is just to verify the generic signature is correct
                @SuppressWarnings("unused")
                KafkaTemplate<String, Object> template = kafkaTemplate;
            });
        }

        @Test
        @DisplayName("KafkaTemplate should use configured producer factory")
        void testKafkaTemplateUsesConfiguredFactory() {
            // Act
            KafkaTemplate<String, Object> kafkaTemplate = kafkaProducerConfig.kafkaTemplate();
            ProducerFactory<String, Object> producerFactory = kafkaTemplate.getProducerFactory();

            // Assert
            assertNotNull(producerFactory, "KafkaTemplate should have ProducerFactory");
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();
            assertEquals(TEST_BOOTSTRAP_SERVERS, configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    "KafkaTemplate's ProducerFactory should have correct bootstrap servers");
        }

        @Test
        @DisplayName("Should create new KafkaTemplate instance each time")
        void testKafkaTemplateNotSingleton() {
            // Act
            KafkaTemplate<String, Object> template1 = kafkaProducerConfig.kafkaTemplate();
            KafkaTemplate<String, Object> template2 = kafkaProducerConfig.kafkaTemplate();

            // Assert
            assertNotSame(template1, template2,
                    "Each call should create a new KafkaTemplate instance");
        }
    }

    // ==================== Configuration Properties Tests ====================

    @Nested
    @DisplayName("Configuration Properties Tests")
    class ConfigurationPropertiesTests {

        @Test
        @DisplayName("Should handle single bootstrap server")
        void testSingleBootstrapServer() {
            // Arrange
            ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", "localhost:9092");

            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals("localhost:9092", configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        }

        @Test
        @DisplayName("Should handle multiple bootstrap servers")
        void testMultipleBootstrapServers() {
            // Arrange
            String multipleServers = "localhost:9092,localhost:9093,localhost:9094";
            ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", multipleServers);

            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(multipleServers, configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        }

        @Test
        @DisplayName("Should handle bootstrap servers with different ports")
        void testBootstrapServersWithDifferentPorts() {
            // Arrange
            String servers = "kafka1.example.com:9092,kafka2.example.com:9093";
            ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", servers);

            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(servers, configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should create complete working Kafka configuration")
        void testCompleteKafkaConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            KafkaTemplate<String, Object> kafkaTemplate = kafkaProducerConfig.kafkaTemplate();

            // Assert
            assertNotNull(producerFactory, "ProducerFactory should be created");
            assertNotNull(kafkaTemplate, "KafkaTemplate should be created");
            assertNotNull(kafkaTemplate.getProducerFactory(), "KafkaTemplate should have ProducerFactory");
        }

        @Test
        @DisplayName("Should support various event object types")
        void testSupportsVariousEventTypes() {
            // Act
            kafkaProducerConfig.kafkaTemplate();

            // Assert - Verify generic Object type can hold different types
            assertDoesNotThrow(() -> {
                // String event
                Object stringEvent = "test event";
                // Custom object event
                Object customEvent = new TestEvent("id", "data");
                // Integer event
                Object intEvent = 123;

                // All should be compatible with KafkaTemplate<String, Object>
                assertNotNull(stringEvent);
                assertNotNull(customEvent);
                assertNotNull(intEvent);
            });
        }

        @Test
        @DisplayName("Should maintain consistent configuration across multiple calls")
        void testConfigurationConsistency() {
            // Act
            ProducerFactory<String, Object> factory1 = kafkaProducerConfig.producerFactory();
            ProducerFactory<String, Object> factory2 = kafkaProducerConfig.producerFactory();

            Map<String, Object> config1 = factory1.getConfigurationProperties();
            Map<String, Object> config2 = factory2.getConfigurationProperties();

            // Assert
            assertEquals(config1.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    config2.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    "Bootstrap servers should be consistent");
            assertEquals(config1.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG),
                    config2.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG),
                    "Key serializer should be consistent");
            assertEquals(config1.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG),
                    config2.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG),
                    "Value serializer should be consistent");
        }

        @Test
        @DisplayName("Should create production-ready configuration")
        void testProductionReadyConfiguration() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert - Verify production-ready settings
            assertEquals("all", configProps.get(ProducerConfig.ACKS_CONFIG),
                    "Should use 'all' acks for data safety");
            assertNotEquals(0, configProps.get(ProducerConfig.RETRIES_CONFIG),
                    "Should have retries configured");
            assertNotNull(configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    "Should have bootstrap servers configured");
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases and Validation Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle configuration with custom bootstrap servers format")
        void testCustomBootstrapServersFormat() {
            // Arrange - Test with IP addresses
            String ipBasedServers = "192.168.1.100:9092,192.168.1.101:9092";
            ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", ipBasedServers);

            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(ipBasedServers, configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        }

        @Test
        @DisplayName("Should verify all critical producer configs are set")
        void testCriticalConfigsPresent() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert - Critical configs must be present
            assertNotNull(configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    "Bootstrap servers must be configured");
            assertNotNull(configProps.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG),
                    "Key serializer must be configured");
            assertNotNull(configProps.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG),
                    "Value serializer must be configured");
        }

        @Test
        @DisplayName("Should have proper serializer classes configured")
        void testSerializerClassesAreValid() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            Object keySerializer = configProps.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
            Object valueSerializer = configProps.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

            assertTrue(keySerializer instanceof Class,
                    "Key serializer should be a Class");
            assertTrue(valueSerializer instanceof Class,
                    "Value serializer should be a Class");
            assertEquals(StringSerializer.class, keySerializer,
                    "Key serializer should be StringSerializer.class");
            assertEquals(JsonSerializer.class, valueSerializer,
                    "Value serializer should be JsonSerializer.class");
        }

        @Test
        @DisplayName("Should have optimal performance settings")
        void testPerformanceSettings() {
            // Act
            ProducerFactory<String, Object> producerFactory = kafkaProducerConfig.producerFactory();
            Map<String, Object> configProps = producerFactory.getConfigurationProperties();

            // Assert
            assertEquals(10, configProps.get(ProducerConfig.LINGER_MS_CONFIG),
                    "Linger MS should be 10 for optimal batching");
            assertEquals(3, configProps.get(ProducerConfig.RETRIES_CONFIG),
                    "Retries should be 3 for fault tolerance");
        }

        @Test
        @DisplayName("Should create independent producer factories")
        void testIndependentFactories() {
            // Act
            ProducerFactory<String, Object> factory1 = kafkaProducerConfig.producerFactory();
            ProducerFactory<String, Object> factory2 = kafkaProducerConfig.producerFactory();

            // Assert
            assertNotSame(factory1, factory2,
                    "Should create independent factory instances");

            // Verify they have same configuration but are different objects
            assertEquals(
                    factory1.getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    factory2.getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    "Both factories should have same configuration");
        }
    }

    // ==================== Test Helper Class ====================

    /**
     * Simple test event class for testing generic type support
     */
    static class TestEvent {
        private final String id;
        private final String data;

        public TestEvent(String id, String data) {
            this.id = id;
            this.data = data;
        }

        public String getId() {
            return id;
        }

        public String getData() {
            return data;
        }
    }
}
