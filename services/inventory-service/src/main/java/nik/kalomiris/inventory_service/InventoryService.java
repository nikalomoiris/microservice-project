
package nik.kalomiris.inventory_service;

import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import nik.kalomiris.inventory_service.exceptions.InsufficientStockException;
import nik.kalomiris.inventory_service.exceptions.InventoryNotFoundException;

import java.util.Map;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import nik.kalomiris.inventory_service.metrics.InventoryMetrics;

@Service
/**
 * Service containing inventory business logic.
 *
 * Responsibilities:
 * - read/update Inventory records
 * - provide idempotent creation for incoming ProductCreated events
 * - reserve / release / commit stock with simple validation and exceptions
 * - publish structured logs via the project's LogPublisher (best-effort)
 */
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final LogPublisher logPublisher;
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);
    private static final String SERVICE_NAME = "inventory-service";
    private static final String LOGGER_NAME = "nik.kalomiris.inventory_service.InventoryService";
    private static final String PRODUCT_ID_KEY = "productId";
    private final ConcurrentHashMap<String, Object> createLocks = new ConcurrentHashMap<>();
    private final InventoryMetrics inventoryMetrics;

    public InventoryService(InventoryRepository inventoryRepository, InventoryMapper inventoryMapper,
            LogPublisher logPublisher, InventoryMetrics inventoryMetrics) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
        this.logPublisher = logPublisher;
        this.inventoryMetrics = inventoryMetrics;
    }

    public Optional<InventoryDTO> getInventoryBySku(String sku) {
        return inventoryRepository.findBySku(sku)
                .map(inventoryMapper::toDto);
    }

    /**
     * Idempotent creation of an Inventory record.
     *
     * Notes:
     * - Designed to be safe when the same ProductCreatedEvent is processed
     * multiple times (checks by productId and sku).
     * - Uses a lightweight in-process lock per SKU to reduce concurrent
     * create races, and catches optimistic locking exceptions which are
     * treated as benign (another transaction created the row).
     */
    public void createInventoryRecord(Long productId, String sku) {
        // Idempotent creation: first check by productId, then by SKU. This reduces
        // races where the same ProductCreatedEvent is processed more than once.
        if (inventoryRepository.findByProductId(productId).isPresent()) {
            return;
        }

        // Lightweight container-local lock per SKU to reduce concurrent create races
        Object lock = createLocks.computeIfAbsent(sku, k -> new Object());
        synchronized (lock) {
            if (inventoryRepository.findByProductId(productId).isPresent()
                    || inventoryRepository.findBySku(sku).isPresent()) {
                return;
            }

            try {
                logger.info("Creating inventory record for sku={} productId={}", sku, productId);
                Inventory newInventory = new Inventory(sku, 0, 0); // Default to 0 quantity
                // set productId column instead of forcing identity id.
                newInventory.setProductId(productId);
                // persist via JPA and flush to ensure immediate visibility
                inventoryRepository.saveAndFlush(newInventory);
                logger.info("Inventory saveAndFlush completed for sku={} productId={}", sku, productId);
                safeMarkUpdated();

                // publish informational logs without impacting business logic
                // Publish a single informational log containing both sku and productId
                safePublish(new LogMessage.Builder()
                        .message("Inventory record created")
                        .level("INFO")
                        .service(SERVICE_NAME)
                        .logger(LOGGER_NAME)
                        .metadata(Map.of("sku", sku, PRODUCT_ID_KEY, String.valueOf(productId)))
                        .build());
            } catch (ObjectOptimisticLockingFailureException e) {
                // Another transaction created/updated the row concurrently. Treat
                // this as a benign race and return; the inventory record exists now.
                safePublish(new LogMessage.Builder()
                        .message("Inventory create raced with another transaction")
                        .level("WARN")
                        .service(SERVICE_NAME)
                        .logger(LOGGER_NAME)
                        .metadata(Map.of("sku", sku, PRODUCT_ID_KEY, String.valueOf(productId)))
                        .build());
            } catch (Exception e) {
                // Surface unexpected exceptions so we can debug event handling failures.
                logger.error("Failed to create inventory record for sku={} productId={}", sku, productId, e);
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Inventory create failed: " + e.getMessage())
                            .level("ERROR")
                            .service(SERVICE_NAME)
                            .logger(LOGGER_NAME)
                            .metadata(Map.of("sku", sku, PRODUCT_ID_KEY, String.valueOf(productId)))
                            .build();
                    safePublish(logMessage);
                } catch (Exception ignored) {
                    // intentionally ignore logging failures; they shouldn't break creation
                }
            }
        }
    }

    public void reserveStock(Long productId, Integer amountToReserver) {
        /**
         * Reserve a quantity for a given productId.
         *
         * Throws {@link InsufficientStockException} when available stock
         * is insufficient, or {@link InventoryNotFoundException} when no
         * inventory record exists for the product.
         */
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getQuantity() - inventory.getReservedQuantity() >= amountToReserver) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() + amountToReserver);
                inventoryRepository.save(inventory);
                safeMarkUpdated();

                // Publish a log event about the stock reservation. Ignore logging failures.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Stock reserved")
                            .level("INFO")
                            .service(SERVICE_NAME)
                            .logger(LOGGER_NAME)
                            .metadata(Map.of(PRODUCT_ID_KEY, productId.toString(), "amountReserved",
                                    amountToReserver.toString()))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception e) {
                    // ignore logging failures
                }
            } else {
                throw new InsufficientStockException("Not enough stock to reserve");
            }
        } else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void releaseStock(Long productId, Integer amountToRelease) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getReservedQuantity() >= amountToRelease) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - amountToRelease);
                inventoryRepository.save(inventory);
                safeMarkUpdated();

                // Publish a log event about the stock release. Ignore logging failures.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Stock released")
                            .level("INFO")
                            .service(SERVICE_NAME)
                            .logger(LOGGER_NAME)
                            .metadata(Map.of(PRODUCT_ID_KEY, productId.toString(), "amountReleased",
                                    amountToRelease.toString()))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception e) {
                    // ignore logging failures
                }
            } else {
                throw new InsufficientStockException("Not enough reserved stock to release");
            }
        } else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void commitStock(Long productId, Integer amountToCommit) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getReservedQuantity() >= amountToCommit) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - amountToCommit);
                inventory.setQuantity(inventory.getQuantity() - amountToCommit);
                inventoryRepository.save(inventory);
                safeMarkUpdated();

                // Publish a log event about the stock commit. Ignore logging failures.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Stock committed")
                            .level("INFO")
                            .service(SERVICE_NAME)
                            .logger(LOGGER_NAME)
                            .metadata(Map.of(PRODUCT_ID_KEY, productId.toString(), "amountCommitted",
                                    amountToCommit.toString()))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception e) {
                    // ignore logging failures
                }
            } else {
                throw new InsufficientStockException("Not enough reserved stock to commit");
            }
        } else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void setQuantity(Long productId, Integer newQuantity) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            inventory.setQuantity(newQuantity);
            inventoryRepository.save(inventory);
            safeMarkUpdated();
        } else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    private void safePublish(LogMessage msg) {
        try {
            logPublisher.publish(msg);
        } catch (Exception e) {
            // don't let logging failures affect business logic; keep a local warn log
            logger.warn("Failed to publish log message: {}", msg.getMessage());
        }
    }

    private void safeMarkUpdated() {
        try {
            if (inventoryMetrics != null) {
                inventoryMetrics.markUpdated();
            }
        } catch (Exception e) {
            /* metrics update is best-effort and should not affect business flow */
        }
    }

    // Legacy fallback removed: callers must now supply productId. This keeps the
    // code focused and avoids ambiguity between entity id and product id.
}
