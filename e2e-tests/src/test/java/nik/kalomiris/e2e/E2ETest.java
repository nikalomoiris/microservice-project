package nik.kalomiris.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    // When the test starts compose via the host docker CLI, mark this so we use localhost mappings
    private static boolean LOCAL_COMPOSE_STARTED = false;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .callTimeout(Duration.ofMinutes(2))
            .build();
    private static final Logger log = LoggerFactory.getLogger(E2ETest.class);

    // constants
    private static final String LOCALHOST = "localhost";
    private static final String HTTP_PREFIX = "http://";
    private static final String APPLICATION_JSON = "application/json";
    private static final String INVENTORY_PATH = "/inventory/";
    private static final int POLL_INTERVAL_MS = 500;

    @BeforeAll
    public static void startCompose() throws Exception {
        if (USE_EXTERNAL_COMPOSE) {
            // assume user started docker-compose manually (services are mapped to localhost ports per repo compose)
            log.info("SKIP_COMPOSE=true - skipping Testcontainers docker-compose startup and using localhost: mapped ports");
            return;
        }
        File compose = new File("/Users/heliconmuse/Coding/microservices-project/docker-compose.yml");

        // Try to start compose using the host docker CLI (works with Docker Desktop on macOS/Apple Silicon).
        // This avoids Testcontainers pulling the docker/compose image (which is often amd64-only).
        if (tryStartLocalDockerCompose(compose)) {
            LOCAL_COMPOSE_STARTED = true;
            System.out.println("Started docker-compose via local docker CLI; waiting for services to be reachable on localhost...");
            // wait for key services to be reachable on their host ports (product, inventory, order, rabbitmq, postgres)
            waitForService("localhost", 8080, Duration.ofMinutes(3));
            waitForService("localhost", 8083, Duration.ofMinutes(3));
            waitForService("localhost", 8081, Duration.ofMinutes(3));
            waitForService("localhost", 5672, Duration.ofMinutes(3));
            waitForService("localhost", 5432, Duration.ofMinutes(3));
            System.out.println("Services are reachable; proceeding with tests.");
            return;
        }

        // Fallback: let Testcontainers start docker-compose (may pull docker/compose image and fail on Apple Silicon)
        environment = new DockerComposeContainer<>(compose)
                .withExposedService("product-service", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("order-service", 8081, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("inventory-service", 8083, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("postgres-service", 5432, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("rabbitmq-service", 5672, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
                .withExposedService("kafka-service", 9092, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        environment.start();
    }

    private static boolean tryStartLocalDockerCompose(File composeFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f", composeFile.getAbsolutePath(), "up", "-d");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            log.debug("docker compose output: {}", out);
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            log.warn("Local docker compose failed: {}", e.getMessage());
            return false;
        }
    }

    private static void waitForService(String host, int port, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(host, port), 2000);
                return; // success
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Service not reachable: " + host + ":" + port + " after " + timeout);
    }

    @AfterAll
    public static void stopCompose() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    public void createProductSetInventoryPlaceOrderAndCommit() throws Exception {
        long categoryId = createCategoryIfMissing("e2e-cat");
        JsonNode prod = createProduct(categoryId);
        long productId = prod.get("id").asLong();
        String sku = prod.get("sku").asText();

        boolean inventoryReady = waitForInventorySku(sku, Duration.ofSeconds(30));
        assertTrue(inventoryReady, "inventory record created by event listener within timeout");

        setInventoryQuantity(productId, 100);
        reserveInventory(productId, 2);
        placeOrder(productId, sku, 2);

        boolean commitObserved = pollForCommit(sku, 98, 0, Duration.ofSeconds(30));
        assertTrue(commitObserved, "inventory commit observed within timeout (quantity=98, reserved=0)");
    }

    // --- helpers ---------------------------------------------------------
    private String serviceBase(String serviceName, int port) {
        String host;
        int svcPort;
        if (USE_EXTERNAL_COMPOSE || LOCAL_COMPOSE_STARTED) {
            host = LOCALHOST;
            svcPort = port;
        } else {
            host = environment.getServiceHost(serviceName, port);
            svcPort = environment.getServicePort(serviceName, port);
        }
        return HTTP_PREFIX + host + ":" + svcPort + "/api";
    }

    private long createCategoryIfMissing(String name) throws Exception {
        String productBase = serviceBase("product-service", 8080);
        String categoryJson = MAPPER.createObjectNode().put("name", name).put("description", "e2e test category").toString();
        RequestBody catBody = RequestBody.create(categoryJson, MediaType.get(APPLICATION_JSON));
        Request catReq = new Request.Builder().url(productBase + "/categories").post(catBody).build();
        try (Response r = HTTP.newCall(catReq).execute()) {
            if (r.isSuccessful()) {
                JsonNode cat = MAPPER.readTree(r.body().bytes());
                return cat.get("id").asLong();
            }
        }

        // fallback: list categories and find by name
        Request listReq = new Request.Builder().url(productBase + "/categories").get().build();
        try (Response lr = HTTP.newCall(listReq).execute()) {
            assertTrue(lr.isSuccessful(), "list categories");
            JsonNode arr = MAPPER.readTree(lr.body().bytes());
            for (JsonNode node : arr) {
                if (name.equals(node.get("name").asText())) {
                    return node.get("id").asLong();
                }
            }
        }
        throw new IllegalStateException("Failed to create or find category: " + name);
    }

    private JsonNode createProduct(long categoryId) throws Exception {
        String productBase = serviceBase("product-service", 8080);
        JsonNode body = MAPPER.createObjectNode()
                .put("name", "E2E Product")
                .put("description", "created by e2e test")
                .put("price", 12.34)
                .set("categoryIds", MAPPER.createArrayNode().add(categoryId));
        RequestBody prodBody = RequestBody.create(body.toString(), MediaType.get(APPLICATION_JSON));
        Request prodReq = new Request.Builder().url(productBase + "/products").post(prodBody).build();
        try (Response r = HTTP.newCall(prodReq).execute()) {
            assertEquals(201, r.code(), "product created");
            return MAPPER.readTree(r.body().bytes());
        }
    }

    private boolean waitForInventorySku(String sku, Duration timeout) throws Exception {
        String invBase = serviceBase("inventory-service", 8083);
        String url = invBase + INVENTORY_PATH + sku;
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Request req = new Request.Builder().url(url).get().build();
            try (Response r = HTTP.newCall(req).execute()) {
                if (r.isSuccessful()) return true;
            } catch (Exception ignored) {
                // ignore
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return false;
    }

    private void setInventoryQuantity(long productId, int qty) throws Exception {
        String invBase = serviceBase("inventory-service", 8083);
        RequestBody setBody = RequestBody.create(String.valueOf(qty), MediaType.get(APPLICATION_JSON));
        Request setReq = new Request.Builder().url(invBase + "/inventory/" + productId + "/quantity").post(setBody).build();
        try (Response r = HTTP.newCall(setReq).execute()) {
            assertTrue(r.isSuccessful(), "set inventory quantity");
        }
    }

    private void reserveInventory(long productId, int qty) throws Exception {
        String invBase = serviceBase("inventory-service", 8083);
        RequestBody reserveBody = RequestBody.create(String.valueOf(qty), MediaType.get(APPLICATION_JSON));
        Request reserveReq = new Request.Builder().url(invBase + "/inventory/" + productId + "/reserver").post(reserveBody).build();
        try (Response r = HTTP.newCall(reserveReq).execute()) {
            assertTrue(r.isSuccessful(), "reserve inventory");
        }
    }

    private void placeOrder(long productId, String sku, int qty) throws Exception {
        String orderBase = serviceBase("order-service", 8081);
        JsonNode item = MAPPER.createObjectNode()
                .putNull("id")
                .put("sku", sku)
                .put("price", 12.34)
                .put("quantity", qty)
                .put("productId", productId);
        JsonNode body = MAPPER.createObjectNode().set("orderLineItemsDtoList", MAPPER.createArrayNode().add(item));
        RequestBody orderBody = RequestBody.create(body.toString(), MediaType.get(APPLICATION_JSON));
        Request orderReq = new Request.Builder().url(orderBase + "/orders").post(orderBody).build();
        try (Response r = HTTP.newCall(orderReq).execute()) {
            assertEquals(201, r.code(), "order created");
        }
    }

    private boolean pollForCommit(String sku, int expectedQty, int expectedReserved, Duration timeout) throws Exception {
        String invBase = serviceBase("inventory-service", 8083);
        String url = invBase + INVENTORY_PATH + sku;
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Request req = new Request.Builder().url(url).get().build();
            try (Response r = HTTP.newCall(req).execute()) {
                if (r.isSuccessful()) {
                    JsonNode inv = MAPPER.readTree(r.body().bytes());
                    int quantity = inv.get("quantity").asInt();
                    int reserved = inv.get("reservedQuantity").asInt();
                    if (quantity == expectedQty && reserved == expectedReserved) return true;
                }
            } catch (Exception ignored) {
                // retry
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return false;
    }
}
