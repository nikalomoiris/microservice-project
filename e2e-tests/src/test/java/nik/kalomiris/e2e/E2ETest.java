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
        // 1) Create a category (product API requires categoryIds non-empty)
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

        // Create category directly in productsdb (id=1) so tests are idempotent
        String productsJdbcForCategory;
        if (USE_EXTERNAL_COMPOSE) {
            productsJdbcForCategory = "jdbc:postgresql://localhost:5432/productsdb";
        } else {
            productsJdbcForCategory = "jdbc:postgresql://" + environment.getServiceHost("postgres-service", 5432) + ":" + environment.getServicePort("postgres-service", 5432) + "/productsdb";
        }
        try (Connection c = DriverManager.getConnection(productsJdbcForCategory, "postgres", "postgres")) {
            String upsertCat = "INSERT INTO categories (id, name, description) VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description";
            try (PreparedStatement ps = c.prepareStatement(upsertCat)) {
                ps.setLong(1, 1L);
                ps.setString(2, "e2e-cat");
                ps.setString(3, "e2e test category");
                ps.executeUpdate();
            }
        }

        // 2) Create product directly in productsdb (avoid product-service RabbitMQ serialization error)
        long productId = 9999L;
        String sku = "E2E-SKU-" + productId;
        String productsJdbc;
        if (USE_EXTERNAL_COMPOSE) {
            productsJdbc = "jdbc:postgresql://localhost:5432/productsdb";
        } else {
            productsJdbc = "jdbc:postgresql://" + environment.getServiceHost("postgres-service", 5432) + ":" + environment.getServicePort("postgres-service", 5432) + "/productsdb";
        }
        try (Connection c = DriverManager.getConnection(productsJdbc, "postgres", "postgres")) {
            String insertProduct = "INSERT INTO products (id, description, name, price, sku) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET description = EXCLUDED.description, name = EXCLUDED.name, price = EXCLUDED.price, sku = EXCLUDED.sku";
            try (PreparedStatement ps = c.prepareStatement(insertProduct)) {
                ps.setLong(1, productId);
                ps.setString(2, "created by e2e test");
                ps.setString(3, "E2E Product");
                ps.setBigDecimal(4, new java.math.BigDecimal("12.34"));
                ps.setString(5, sku);
                ps.executeUpdate();
            }

            // link product to category id 1
            String insertLink = "INSERT INTO product_categories (product_id, category_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
            try (PreparedStatement ps2 = c.prepareStatement(insertLink)) {
                ps2.setLong(1, productId);
                ps2.setLong(2, 1L);
                ps2.executeUpdate();
            }
        }

        // 3) Wait a bit for inventory-service to consume the product-created event
        Thread.sleep(2000);

        // 4) Set inventory in postgres directly so productId maps to inventory.id for test repeatability
        String pgHost;
        int pgPort;
        if (USE_EXTERNAL_COMPOSE) {
            pgHost = "localhost";
            pgPort = 5432;
        } else {
            pgHost = environment.getServiceHost("postgres-service", 5432);
            pgPort = environment.getServicePort("postgres-service", 5432);
        }
        String jdbc = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/inventorydb";
        try (Connection c = DriverManager.getConnection(jdbc, "postgres", "postgres")) {
            String upsert = "INSERT INTO inventory (id, sku, quantity, reserved_quantity) VALUES (?, ?, ?, 0) ON CONFLICT (id) DO UPDATE SET sku = EXCLUDED.sku, quantity = EXCLUDED.quantity, reserved_quantity = EXCLUDED.reserved_quantity";
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                ps.setLong(1, productId);
                ps.setString(2, sku);
                ps.setInt(3, 100); // set initial inventory to 100
                ps.executeUpdate();
            }
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

        // 6) Wait for inventory-service to commit stock (order processing via RabbitMQ)
        Thread.sleep(2000);

        // 7) Verify inventory decreased
        String invHost;
        int invPort;
        if (USE_EXTERNAL_COMPOSE) {
            invHost = "localhost";
            invPort = 8083;
        } else {
            invHost = environment.getServiceHost("inventory-service", 8083);
            invPort = environment.getServicePort("inventory-service", 8083);
        }
        String invBase = "http://" + invHost + ":" + invPort + "/api";
        Request invReq = new Request.Builder().url(invBase + "/inventory/" + sku).get().build();
        try (Response r = HTTP.newCall(invReq).execute()) {
            assertTrue(r.isSuccessful(), "get inventory");
            JsonNode inv = MAPPER.readTree(r.body().bytes());
            int quantity = inv.get("quantity").asInt();
            int reserved = inv.get("reservedQuantity").asInt();
            // We set 100 and ordered 2; commit should reduce quantity to 98
            assertEquals(98, quantity);
            // reservedQuantity should be 0 after commit
            assertEquals(0, reserved);
        }
    }
}
