
package nik.kalomiris.inventory_service;

import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import nik.kalomiris.inventory_service.exceptions.InsufficientStockException;
import nik.kalomiris.inventory_service.exceptions.InventoryNotFoundException;

import java.util.Map;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final LogPublisher logPublisher;
    private final JdbcTemplate jdbcTemplate;
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    public InventoryService(InventoryRepository inventoryRepository, InventoryMapper inventoryMapper, LogPublisher logPublisher, JdbcTemplate jdbcTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
        this.logPublisher = logPublisher;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InventoryDTO> getInventoryBySku(String sku) {
        return inventoryRepository.findBySku(sku)
                .map(inventoryMapper::toDto);
    }

    public void createInventoryRecord(Long productId, String sku) {
        // Idempotent creation: first check by productId, then by SKU. This reduces
        // races where the same ProductCreatedEvent is processed more than once.
        if (inventoryRepository.findById(productId).isPresent()) {
            return;
        }

        // Use a JVM-level lock per SKU to avoid concurrent creates inside the same
        // container. This is a simple, low-risk approach suitable for tests and
        // local development; in production you'd use a distributed lock or
        // tolerate retries on optimistic locking.
        synchronized (sku.intern()) {
            if (inventoryRepository.findById(productId).isPresent() || inventoryRepository.findBySku(sku).isPresent()) {
                return;
            }


            try {
                logger.info("Creating inventory record for sku={} productId={}", sku, productId);
                Inventory newInventory = new Inventory(sku, 0, 0); // Default to 0 quantity
                // assign the inventory id to the product id so other services can find by productId
                newInventory.setId(productId);
                // attempt a direct INSERT with the explicit id using JdbcTemplate. This avoids
                // JPA merge/update semantics when the id is manually assigned and ensures the
                // row exists with the same id as the product.
                try {
                    jdbcTemplate.update("INSERT INTO inventory (id, sku, quantity, reserved_quantity) VALUES (?, ?, ?, ?)", productId, sku, 0, 0);
                    logger.info("Inventory inserted via JDBC for sku={} productId={}", sku, productId);
                } catch (DataAccessException dae) {
                    // If another transaction inserted the row concurrently this will raise a
                    // constraint violation; treat as benign and continue.
                    logger.warn("JDBC insert failed (may already exist) for sku={} productId={}: {}", sku, productId, dae.getMessage());
                }

                // log to aid debugging that the entity was saved
                try {
                    LogMessage infoMsg = new LogMessage.Builder()
                            .message("Inventory record created (saveAndFlush)")
                            .level("INFO")
                            .service("inventory-service")
                            .logger("nik.kalomiris.inventory_service.InventoryService")
                            .metadata(Map.of("sku", sku, "productId", String.valueOf(productId)))
                            .build();
                    logPublisher.publish(infoMsg);
                } catch (Exception ignored) {
                }

                // Publish a log event about the inventory creation. Ignore logging failures.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Inventory record created")
                            .level("INFO")
                            .service("inventory-service")
                            .logger("nik.kalomiris.inventory_service.InventoryService")
                            .metadata(Map.of("sku", sku))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception e) {
                    // ignore logging failures
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // Another transaction created/updated the row concurrently. Treat
                // this as a benign race and return; the inventory record exists now.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Inventory create raced with another transaction")
                            .level("WARN")
                            .service("inventory-service")
                            .logger("nik.kalomiris.inventory_service.InventoryService")
                            .metadata(Map.of("sku", sku, "productId", String.valueOf(productId)))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception ignored) {
                    // ignore logging failures
                }
            } catch (Exception e) {
                // Surface unexpected exceptions so we can debug event handling failures.
                logger.error("Failed to create inventory record for sku={} productId={}", sku, productId, e);
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Inventory create failed: " + e.getMessage())
                            .level("ERROR")
                            .service("inventory-service")
                            .logger("nik.kalomiris.inventory_service.InventoryService")
                            .metadata(Map.of("sku", sku, "productId", String.valueOf(productId)))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void reserveStock(Long productId, Integer amountToReserver) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getQuantity() - inventory.getReservedQuantity() >= amountToReserver) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() + amountToReserver);
                inventoryRepository.save(inventory);
                
                // Publish a log event about the stock reservation. Ignore logging failures.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Stock reserved")
                            .level("INFO")
                            .service("inventory-service")
                            .logger("nik.kalomiris.inventory_service.InventoryService")
                            .metadata(Map.of("productId", productId.toString(), "amountReserved", amountToReserver.toString()))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception e) {
                    // ignore logging failures
                }
            }
            else { 
                throw new InsufficientStockException("Not enough stock to reserve"); 
            }
        }
        else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void releaseStock(Long productId, Integer amountToRelease) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getReservedQuantity() >= amountToRelease) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - amountToRelease);
                inventoryRepository.save(inventory);

                // Publish a log event about the stock release. Ignore logging failures.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Stock released")
                            .level("INFO")
                            .service("inventory-service")
                            .logger("nik.kalomiris.inventory_service.InventoryService")
                            .metadata(Map.of("productId", productId.toString(), "amountReleased", amountToRelease.toString()))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception e) {
                    // ignore logging failures
                }
            }
            else { 
                throw new InsufficientStockException("Not enough reserved stock to release"); 
            }
        }
        else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void commitStock(Long productId, Integer amountToCommit) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getReservedQuantity() >= amountToCommit) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - amountToCommit);
                inventory.setQuantity(inventory.getQuantity() - amountToCommit);
                inventoryRepository.save(inventory);

                // Publish a log event about the stock commit. Ignore logging failures.
                try {
                    LogMessage logMessage = new LogMessage.Builder()
                            .message("Stock committed")
                            .level("INFO")
                            .service("inventory-service")
                            .logger("nik.kalomiris.inventory_service.InventoryService")
                            .metadata(Map.of("productId", productId.toString(), "amountCommitted", amountToCommit.toString()))
                            .build();
                    logPublisher.publish(logMessage);
                } catch (Exception e) {
                    // ignore logging failures
                }
            }
            else { 
                throw new InsufficientStockException("Not enough reserved stock to commit"); 
            }
        }
        else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void setQuantity(Long productId, Integer newQuantity) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            inventory.setQuantity(newQuantity);
            inventoryRepository.save(inventory);
        } else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }
}
