package nik.kalomiris.product_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nik.kalomiris.product_service.product.Product;
import nik.kalomiris.product_service.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Used to convert objects to JSON

    @Autowired
    private ProductRepository productRepository; // To clean up before tests

    @BeforeEach
    void setUp() {
        // Clean up the database before each test to ensure a consistent state
        productRepository.deleteAll();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        Product product = new Product();
        product.setName("Test Product");
        product.setDescription("Description for test product");
        product.setPrice(100.00);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated()) // Expect HTTP 201 Created
                .andExpect(jsonPath("$.id").exists()) // Expect an ID to be generated
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.description").value("Description for test product"))
                .andExpect(jsonPath("$.price").value(100.00));
    }

    @Test
    void shouldGetAllProducts() throws Exception {
        // First, create a product to ensure there's something to retrieve
        Product product1 = new Product();
        product1.setName("Product One");
        product1.setDescription("Desc One");
        product1.setPrice(10.00);
        productRepository.save(product1); // Save directly via repository

        Product product2 = new Product();
        product2.setName("Product Two");
        product2.setDescription("Desc Two");
        product2.setPrice(20.00);
        productRepository.save(product2); // Save directly via repository

        mockMvc.perform(get("/api/products")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Expect HTTP 200 OK
                .andExpect(jsonPath("$", hasSize(2))) // Expect two products in the list
                .andExpect(jsonPath("$[0].name").value("Product One"))
                .andExpect(jsonPath("$[1].name").value("Product Two"));
    }

    // Test for retrieving a single product by ID
    @Test
    void shouldGetProductById() throws Exception {
        Product product = new Product();
        product.setName("Single Product");
        product.setDescription("Single Product Description");
        product.setPrice(50.00);
        Product savedProduct = productRepository.save(product); // Save and get the saved entity with ID
        Long productId = savedProduct.getId();

        mockMvc.perform(get("/api/products/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Expect HTTP 200 OK
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.name").value("Single Product"))
                .andExpect(jsonPath("$.description").value("Single Product Description"))
                .andExpect(jsonPath("$.price").value(50.00));
    }

    // Test for updating a product
    @Test
    void shouldUpdateProduct() throws Exception {
        Product product = new Product();
        product.setName("Old Product");
        product.setDescription("Old Description");
        product.setPrice(30.00);
        Product savedProduct = productRepository.save(product); // Save and get the saved entity with ID
        Long productId = savedProduct.getId(); 
        // Update product details
        savedProduct.setName("Updated Product");
        savedProduct.setDescription("Updated Description");
        savedProduct.setPrice(35.00);

        mockMvc.perform(post("/api/products/updateProduct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(savedProduct)))
                .andExpect(status().isOk()) // Expect HTTP 200 OK
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.name").value("Updated Product"))
                .andExpect(jsonPath("$.description").value("Updated Description"))
                .andExpect(jsonPath("$.price").value(35.00));
    }
}
