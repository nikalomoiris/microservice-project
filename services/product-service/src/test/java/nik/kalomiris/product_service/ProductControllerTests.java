package nik.kalomiris.product_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nik.kalomiris.product_service.product.ProductController;
import nik.kalomiris.product_service.product.ProductDTO;
import nik.kalomiris.product_service.product.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;


class ProductControllerTests {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();
    private ProductService productService;

    @BeforeEach
    void setUp() {
    productService = Mockito.mock(ProductService.class);
    var controller = new ProductController(productService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldCreateProductWithCategories() throws Exception {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setName("Test Product");
        productDTO.setPrice(100.00);
        productDTO.setCategoryIds(Arrays.asList(1L, 2L));

        ProductDTO savedProductDTO = new ProductDTO();
        savedProductDTO.setId(1L);
        savedProductDTO.setName("Test Product");
        savedProductDTO.setPrice(100.00);
        savedProductDTO.setSku("TES-1234");
        savedProductDTO.setCategoryIds(Arrays.asList(1L, 2L));

        Mockito.when(productService.createProduct(Mockito.any(ProductDTO.class))).thenReturn(savedProductDTO);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.sku").value("TES-1234"))
                .andExpect(jsonPath("$.categoryIds", hasSize(2)))
                .andExpect(jsonPath("$.categoryIds[0]").value(1))
                .andExpect(jsonPath("$.categoryIds[1]").value(2));
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setName("Test Product");
        productDTO.setPrice(100.00);
        productDTO.setCategoryIds(Arrays.asList(1L));

        ProductDTO savedProductDTO = new ProductDTO();
        savedProductDTO.setId(1L);
        savedProductDTO.setName("Test Product");
        savedProductDTO.setPrice(100.00);
        savedProductDTO.setSku("TES-1234");
        savedProductDTO.setCategoryIds(Arrays.asList(1L));

        Mockito.when(productService.createProduct(Mockito.any(ProductDTO.class))).thenReturn(savedProductDTO);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.sku").value("TES-1234"))
                .andExpect(jsonPath("$.categoryIds", hasSize(1)))
                .andExpect(jsonPath("$.categoryIds[0]").value(1));
    }

    @Test
    void shouldGetAllProducts() throws Exception {
        ProductDTO product1 = new ProductDTO();
        product1.setId(1L);
        product1.setName("Product One");

        ProductDTO product2 = new ProductDTO();
        product2.setId(2L);
        product2.setName("Product Two");

        when(productService.getAllProducts()).thenReturn(Arrays.asList(product1, product2));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Product One"))
                .andExpect(jsonPath("$[1].name").value("Product Two"));
    }

    @Test
    void shouldGetProductById() throws Exception {
        ProductDTO product = new ProductDTO();
        product.setId(1L);
        product.setName("Single Product");

        when(productService.getProductById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(get("/api/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Single Product"));
    }

    @Test
    void shouldReturnNotFoundForInvalidProductId() throws Exception {
        when(productService.getProductById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/{id}", 99L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        long productId = 1L;
        ProductDTO updatedInfo = new ProductDTO();
        updatedInfo.setName("Updated Name");
        updatedInfo.setCategoryIds(Arrays.asList(1L));

        ProductDTO updatedProduct = new ProductDTO();
        updatedProduct.setId(productId);
        updatedProduct.setName("Updated Name");
        updatedProduct.setCategoryIds(Arrays.asList(1L));

        Mockito.when(productService.productExists(productId)).thenReturn(true);
        Mockito.when(productService.updateProduct(Mockito.any(ProductDTO.class))).thenReturn(updatedProduct);

        mockMvc.perform(put("/api/products/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.categoryIds", hasSize(1)))
                .andExpect(jsonPath("$.categoryIds[0]").value(1));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        long productId = 1L;
        when(productService.productExists(productId)).thenReturn(true);

        mockMvc.perform(delete("/api/products/{id}", productId))
                .andExpect(status().isNoContent());
    }
}
