package cn.lineai.engine.ai;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModelCatalogClientTest {

    @Test public void parsesAndSortsModelIds() throws Exception {
        List<String> ids = ModelCatalogClient.parse(
                "{\"data\":[{\"id\":\"z-model\"},{\"id\":\"a-model\"},{\"id\":\"\"}]}");
        assertEquals(2, ids.size());
        assertEquals("a-model", ids.get(0));
        assertEquals("z-model", ids.get(1));
    }

    @Test public void emptyOrUnknownPayloadYieldsNoIds() throws Exception {
        assertTrue(ModelCatalogClient.parse("{}").isEmpty());
        assertTrue(ModelCatalogClient.parse("{\"data\":[]}").isEmpty());
    }

    @Test public void endpointAppendsSuffixOnce() {
        assertEquals("https://api.example.com/v1/models",
                ModelCatalogClient.endpoint("https://api.example.com/v1/", "/models"));
        assertEquals("https://api.example.com/v1/models",
                ModelCatalogClient.endpoint("https://api.example.com/v1/models", "/models"));
        assertEquals("https://api.example.com/v1/chat/completions",
                ModelCatalogClient.endpoint("https://api.example.com/v1", "/chat/completions"));
    }

    @Test public void anthropicRootOriginDropsPaths() throws Exception {
        assertEquals("https://api.anthropic.com",
                ModelCatalogClient.rootOrigin("https://api.anthropic.com/v1"));
        assertEquals("https://proxy.example.com:8443",
                ModelCatalogClient.rootOrigin("https://proxy.example.com:8443/anthropic"));
    }
}
