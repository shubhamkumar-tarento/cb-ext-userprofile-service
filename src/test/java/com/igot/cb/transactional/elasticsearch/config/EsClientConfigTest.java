package com.igot.cb.transactional.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class EsClientConfigTest {
    @Test
    void testElasticsearchClientBeanCreation() throws Exception {

        EsClientConfig config = new EsClientConfig();

        // Inject private @Value fields using reflection
        setPrivateField(config, "elasticsearchHost", "localhost");
        setPrivateField(config, "elasticsearchPort", 9200);
        setPrivateField(config, "elasticsearchUsername", "elastic");
        setPrivateField(config, "elasticsearchPassword", "password");

        ElasticsearchClient client = config.elasticsearchClient();

        assertNotNull(client);
    }

    @Test
    void testElasticsearchClientMultipleCalls() throws Exception {

        EsClientConfig config = new EsClientConfig();

        setPrivateField(config, "elasticsearchHost", "127.0.0.1");
        setPrivateField(config, "elasticsearchPort", 9200);
        setPrivateField(config, "elasticsearchUsername", "user");
        setPrivateField(config, "elasticsearchPassword", "pass");

        ElasticsearchClient client1 = config.elasticsearchClient();
        ElasticsearchClient client2 = config.elasticsearchClient();

        assertNotNull(client1);
        assertNotNull(client2);
        assertNotSame(client1, client2); // new instance each time
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
