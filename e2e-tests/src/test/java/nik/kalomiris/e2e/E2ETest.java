package nik.kalomiris.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class E2ETest {

    private static DockerComposeContainer<?> environment;
    // If SKIP_COMPOSE=true, tests will assume docker-compose services are already running on localhost
    private static final boolean USE_EXTERNAL_COMPOSE = Boolean.parseBoolean(System.getenv().getOrDefault("SKIP_COMPOSE", "false"));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .callTimeout(Duration.ofMinutes(2))
            .build();

    @BeforeAll
    public static void startCompose() throws Exception {
        if (USE_EXTERNAL_COMPOSE) {
            // assume user started docker-compose manually (services are mapped to localhost ports per repo compose)
            System.out.println("SKIP_COMPOSE=true - skipping Testcontainers docker-compose startup and using localhost: mapped ports");
            return;
        }

        File compose = new File("/Users/heliconmuse/Coding/microservices-project/docker-compose.yml");
        environment = new DockerComposeContainer<>(compose)
                .withExposedService("product-service", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("order-service", 8081, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("inventory-service", 8083, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("postgres-service", 5432, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("rabbitmq-service", 5672, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("kafka-service", 9092, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        environment.start();
    }

    @AfterAll
    public static void stopCompose() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    public void createProduct_setInventory_placeOrder_andCommit() throws Exception {
        // 1) Create a category via product-service REST API
        String productHost;
        int productPort;
        if (USE_EXTERNAL_COMPOSE) {
            productHost = "localhost";
            productPort = 8080;
        } else {
            productHost = environment.getServiceHost("product-service", 8080);
            productPort = environment.getServicePort("product-service", 8080);
        }
        String productBase = "http://" + productHost + ":" + productPort + "/api";

        // create category
        String categoryJson = "{\"name\":\"e2e-cat\",\"description\":\"e2e test category\"}";
        RequestBody catBody = RequestBody.create(categoryJson, MediaType.get("application/json"));
        Request catReq = new Request.Builder().url(productBase + "/categories").post(catBody).build();
        long categoryId;
        try (Response r = HTTP.newCall(catReq).execute()) {
            if (r.isSuccessful()) {
                JsonNode cat = MAPPER.readTree(r.body().bytes());
                categoryId = cat.get("id").asLong();
            } else {
                // If category already exists, find it via GET /api/categories
                Request listReq = new Request.Builder().url(productBase + "/categories").get().build();
                try (Response lr = HTTP.newCall(listReq).execute()) {
                    assertTrue(lr.isSuccessful(), "list categories");
                    JsonNode arr = MAPPER.readTree(lr.body().bytes());
                    long found = -1;
                    for (JsonNode node : arr) {
                        if ("e2e-cat".equals(node.get("name").asText())) {
                            found = node.get("id").asLong();
                            break;
                        }
                    }
                    assertTrue(found != -1, "found existing category");
                    categoryId = found;
                }
            }
        }

        // 2) Create product via product-service REST API (product-service will publish ProductCreatedEvent)
        String createProductJson = "{\"name\":\"E2E Product\",\"description\":\"created by e2e test\",\"price\":12.34,\"categoryIds\":[" + categoryId + "]}";
        RequestBody prodBody = RequestBody.create(createProductJson, MediaType.get("application/json"));
        Request prodReq = new Request.Builder().url(productBase + "/products").post(prodBody).build();
        long productId;
        String sku;
        try (Response r = HTTP.newCall(prodReq).execute()) {
            assertEquals(201, r.code(), "product created");
            JsonNode prod = MAPPER.readTree(r.body().bytes());
            productId = prod.get("id").asLong();
            sku = prod.get("sku").asText();
        }

        // Prepare inventory service base URL (used for polling and subsequent calls)
        String invHostForSet;
        int invPortForSet;
        if (USE_EXTERNAL_COMPOSE) {
            invHostForSet = "localhost";
            invPortForSet = 8083;
        } else {
            invHostForSet = environment.getServiceHost("inventory-service", 8083);
            invPortForSet = environment.getServicePort("inventory-service", 8083);
        }
        String invSetBase = "http://" + invHostForSet + ":" + invPortForSet + "/api";

        // 3) Wait for inventory-service to consume the product-created event and create inventory record.
        // Poll GET /api/inventory/{sku} until it's present (timeout 30s) instead of a fixed sleep. No fallback — be strict.
        String invCheckUrl = invSetBase + "/inventory/" + sku;
        boolean inventoryReady = false;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 30_000) {
            Request invCheckReq = new Request.Builder().url(invCheckUrl).get().build();
            try (Response ir = HTTP.newCall(invCheckReq).execute()) {
                if (ir.isSuccessful()) {
                    inventoryReady = true;
                    break;
                }
            } catch (Exception e) {
                // ignore transient network errors while waiting for the event to be processed
            }
            Thread.sleep(500);
        }
        assertTrue(inventoryReady, "inventory record created by event listener within timeout");

        RequestBody setBody = RequestBody.create("100", MediaType.get("application/json"));
        Request setReq = new Request.Builder().url(invSetBase + "/inventory/" + productId + "/quantity").post(setBody).build();
        try (Response r = HTTP.newCall(setReq).execute()) {
            assertTrue(r.isSuccessful(), "set inventory quantity");
        }

        // Reserve the required quantity so commitStock can succeed when the order is processed.
        // Inventory controller exposes POST /api/inventory/{productId}/reserver that accepts an integer body.
        String invHostForReserve;
        int invPortForReserve;
        if (USE_EXTERNAL_COMPOSE) {
            invHostForReserve = "localhost";
            invPortForReserve = 8083;
        } else {
            invHostForReserve = environment.getServiceHost("inventory-service", 8083);
            invPortForReserve = environment.getServicePort("inventory-service", 8083);
        }
        String invReserveBase = "http://" + invHostForReserve + ":" + invPortForReserve + "/api";
        RequestBody reserveBody = RequestBody.create("2", MediaType.get("application/json"));
        Request reserveReq = new Request.Builder().url(invReserveBase + "/inventory/" + productId + "/reserver").post(reserveBody).build();
        try (Response r = HTTP.newCall(reserveReq).execute()) {
            assertTrue(r.isSuccessful(), "reserve inventory");
        }

        // 5) Place order via order-service
        String orderHost;
        int orderPort;
        if (USE_EXTERNAL_COMPOSE) {
            orderHost = "localhost";
            orderPort = 8081;
        } else {
            orderHost = environment.getServiceHost("order-service", 8081);
            orderPort = environment.getServicePort("order-service", 8081);
        }
        String orderBase = "http://" + orderHost + ":" + orderPort + "/api";

        // Build order payload
        String orderJson = "{\"orderLineItemsDtoList\":[{\"id\":null,\"sku\":\"" + sku + "\",\"price\":12.34,\"quantity\":2,\"productId\": " + productId + "}]}";
        RequestBody orderBody = RequestBody.create(orderJson, MediaType.get("application/json"));
        Request orderReq = new Request.Builder().url(orderBase + "/orders").post(orderBody).build();
        try (Response r = HTTP.newCall(orderReq).execute()) {
            assertEquals(201, r.code(), "order created");
        }

        // 6) Poll for inventory change as order is processed (timeout 30s)
        String invBase = invSetBase; // reuse computed inventory base
        boolean commitObserved = false;
        start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 30_000) {
            Request invReq = new Request.Builder().url(invBase + "/inventory/" + sku).get().build();
            try (Response r = HTTP.newCall(invReq).execute()) {
                if (r.isSuccessful()) {
                    JsonNode inv = MAPPER.readTree(r.body().bytes());
                    int quantity = inv.get("quantity").asInt();
                    int reserved = inv.get("reservedQuantity").asInt();
                    if (quantity == 98 && reserved == 0) {
                        commitObserved = true;
                        break;
                    }
                }
            } catch (Exception e) {
                // swallow and retry until timeout
            }
            Thread.sleep(500);
        }
        assertTrue(commitObserved, "inventory commit observed within timeout (quantity=98, reserved=0)");
    }
}
