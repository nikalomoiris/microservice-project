package nik.kalomiris.product_service.product;

import org.springframework.stereotype.Component;
import nik.kalomiris.product_service.category.Category;
import nik.kalomiris.product_service.category.CategoryRepository;
import nik.kalomiris.product_service.image.Image;
import nik.kalomiris.product_service.image.ImageRepository;

import java.util.List;

@Component
public class ProductMapper {

    private final CategoryRepository categoryRepository;
    private final ImageRepository imageRepository;

    public ProductMapper(CategoryRepository categoryRepository, ImageRepository imageRepository) {
        this.categoryRepository = categoryRepository;
        this.imageRepository = imageRepository;
    }

    public ProductDTO toDto(Product product) {
        if (product == null) {
            return null;
        }
        List<Long> categoryIds = product.getCategories() == null ? List.of() :
            product.getCategories().stream().map(Category::getId).toList();
        List<Long> imagesIds = product.getImages() == null ? List.of() :
            product.getImages().stream().map(image -> image.getId()).toList();
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                categoryIds,
                imagesIds
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
        
         if (dto.getImagesIds() != null && !dto.getImagesIds().isEmpty()) {
            List<Image> images = dto.getImagesIds().stream()
                 .map(id -> imageRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Image not found: " + id)))
                 .toList();
             product.setImages(images);
        }
        return product;
    }
}
