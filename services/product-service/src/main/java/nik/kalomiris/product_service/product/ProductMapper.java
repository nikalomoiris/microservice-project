package nik.kalomiris.product_service.product;

import org.springframework.stereotype.Component;
import nik.kalomiris.product_service.category.Category;
import nik.kalomiris.product_service.category.CategoryRepository;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    private final CategoryRepository categoryRepository;

    public ProductMapper(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public ProductDTO toDto(Product product) {
        if (product == null) {
            return null;
        }
        List<Long> categoryIds = product.getCategories() == null ? List.of() :
            product.getCategories().stream().map(Category::getId).toList();
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                categoryIds
        );
    }

    public Product toEntity(ProductDTO dto) {
        if (dto == null) {
            return null;
        }
        Product product = new Product();
        product.setId(dto.getId());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setSku(dto.getSku());
        if (dto.getCategoryIds() != null && !dto.getCategoryIds().isEmpty()) {
            List<Category> categories = dto.getCategoryIds().stream()
                .map(id -> categoryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Category not found: " + id)))
                .toList();
            product.setCategories(categories);
        } else {
            throw new IllegalArgumentException("Product must have at least one category");
        }
        return product;
    }
}
