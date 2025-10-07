package nik.kalomiris.product_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nik.kalomiris.product_service.category.Category;
import nik.kalomiris.product_service.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;


class CategoryControllerTests {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        categoryRepository = Mockito.mock(CategoryRepository.class);
        var controller = new nik.kalomiris.product_service.category.CategoryController(categoryRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldCreateCategory() throws Exception {
        Category category = new Category("Electronics", "Electronic items");
        category.setId(1L);
        when(categoryRepository.existsByName("Electronics")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(category)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void shouldNotCreateDuplicateCategory() throws Exception {
        Category category = new Category("Electronics", "Electronic items");
        when(categoryRepository.existsByName("Electronics")).thenReturn(true);

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(category)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetAllCategories() throws Exception {
        Category cat1 = new Category("Electronics", "Electronic items");
        cat1.setId(1L);
        Category cat2 = new Category("Books", "Books and magazines");
        cat2.setId(2L);
        when(categoryRepository.findAll()).thenReturn(Arrays.asList(cat1, cat2));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[1].name").value("Books"));
    }

    @Test
    void shouldGetCategoryById() throws Exception {
        Category cat = new Category("Electronics", "Electronic items");
        cat.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void shouldReturnNotFoundForInvalidCategoryId() throws Exception {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound());
    }
}
