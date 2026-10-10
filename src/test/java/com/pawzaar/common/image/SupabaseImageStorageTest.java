package com.pawzaar.common.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

/**
 * Unit tests for {@link SupabaseImageStorage}.
 *
 * <p>{@link MockRestServiceServer} binds to the {@link RestClient.Builder}, so no network is touched.
 * Each test asserts the exact HTTP contract (path, method, auth header, content type) that Supabase
 * expects - the part most likely to break if the API shape is wrong.
 */
class SupabaseImageStorageTest {

    private static final String BASE = "https://demo.supabase.co";
    private static final String OBJECT_PREFIX = BASE + "/storage/v1/object/pawzaar-images/";

    private MockRestServiceServer server;
    private SupabaseImageStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer service-key")
                .defaultHeader("apikey", "service-key");
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new SupabaseImageStorage(builder.build(), "pawzaar-images");
    }

    @Test
    void storeUploadsWithGeneratedKeyAndDerivedContentType() {
        server.expect(requestTo(startsWith(OBJECT_PREFIX)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-key"))
                .andExpect(header("x-upsert", "true"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andRespond(withSuccess());

        String key = storage.store(new byte[]{1, 2, 3}, "png");

        assertTrue(key.matches("[0-9a-f]{32}\\.png"), "unexpected key: " + key);
        server.verify();
    }

    @Test
    void loadReturnsTheStoredBytes() throws Exception {
        server.expect(requestTo(startsWith(OBJECT_PREFIX)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[]{9, 8, 7}, MediaType.IMAGE_PNG));

        Resource resource = storage.load("abc.png");

        assertArrayEquals(new byte[]{9, 8, 7}, resource.getContentAsByteArray());
        server.verify();
    }

    @Test
    void loadOfAMissingImageBecomesImageStorageException() {
        server.expect(requestTo(startsWith(OBJECT_PREFIX)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ImageStorageException.class, () -> storage.load("missing.png"));
    }

    @Test
    void deleteCallsTheObjectEndpoint() {
        server.expect(requestTo(startsWith(OBJECT_PREFIX)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        storage.delete("abc.png");

        server.verify();
    }

    @Test
    void deleteOfAnAlreadyGoneImageIsIgnored() {
        server.expect(requestTo(startsWith(OBJECT_PREFIX)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertDoesNotThrow(() -> storage.delete("gone.png"));
        server.verify();
    }

    @Test
    void storeFailureBecomesImageStorageException() {
        server.expect(requestTo(startsWith(OBJECT_PREFIX)))
                .andRespond(withServerError());

        assertThrows(ImageStorageException.class, () -> storage.store(new byte[]{1}, "png"));
    }
}
