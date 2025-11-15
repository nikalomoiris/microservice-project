package nik.kalomiris.product_service;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import nik.kalomiris.product_service.category.Category;
import nik.kalomiris.product_service.category.CategoryRepository;
import nik.kalomiris.product_service.product.Product;
import nik.kalomiris.product_service.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import nik.kalomiris.product_service.config.TestMessagingConfig;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMessagingConfig.class)
public class ProductControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category booksCategory;
    private Category electronicsCategory;

    @BeforeEach
    void setUp() {
        booksCategory = new Category();
        booksCategory.setName("Books");
        categoryRepository.save(booksCategory);

        electronicsCategory = new Category();
        electronicsCategory.setName("Electronics");
        categoryRepository.save(electronicsCategory);

        Product product1 = new Product();
        product1.setName("The Lord of the Rings");
        product1.setPrice(25.00);
        product1.setSku("BOOK-123");
        product1.setCategories(Arrays.asList(booksCategory));
        productRepository.save(product1);

        Product product2 = new Product();
        product2.setName("Laptop");
        product2.setPrice(1200.00);
        product2.setSku("ELEC-456");
        product2.setCategories(Arrays.asList(electronicsCategory));
        productRepository.save(product2);

        Product product3 = new Product();
        product3.setName("A Game of Thrones");
        product3.setPrice(20.00);
        product3.setSku("BOOK-456");
        product3.setCategories(Arrays.asList(booksCategory));
        productRepository.save(product3);
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void shouldGetAllProducts() throws Exception {
        mockMvc
                .perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void shouldGetAllProductsFilteredByCategory() throws Exception {
        mockMvc
                .perform(get("/api/products?categoryName=Books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("The Lord of the Rings"))
                .andExpect(jsonPath("$[1].name").value("A Game of Thrones"));
    }

    @Test
    void shouldGetAllProductsSortedByPriceDescending() throws Exception {
        mockMvc
                .perform(get("/api/products?sortBy=price&sortDir=desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Laptop"))
                .andExpect(jsonPath("$[1].name").value("The Lord of the Rings"))
                .andExpect(jsonPath("$[2].name").value("A Game of Thrones"));
    }

    @Test
    void shouldGetAllProductsFilteredByCategoryAndSortedByName()
            throws Exception {
        mockMvc
                .perform(
                        get("/api/products?categoryName=Books&sortBy=name&sortDir=asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("A Game of Thrones"))
                .andExpect(jsonPath("$[1].name").value("The Lord of the Rings"));
    }
}
