package nik.kalomiris.product_service.product;


import java.util.List;


public class ProductDTO {
    /**
     * DTO used by REST APIs to expose product data.
     * Keep DTOs property-focused and avoid embedding business logic.
     */

    private Long id;
    private String name;
    private String description;
    private double price;
    private String sku;
    private List<Long> categoryIds;
    private List<Long> imagesIds;
    private List<String> imageUrls; // Image URLs for display

    public ProductDTO() {
    }

    public ProductDTO(Long id, String name, 
            String description, double price, 
            String sku, List<Long> categoryIds, List<Long> imagesIds, List<String> imageUrls) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.sku = sku;
        this.categoryIds = categoryIds;
        this.imagesIds = imagesIds;
        this.imageUrls = imageUrls;
    }
    public List<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public List<Long> getImagesIds() {
        return imagesIds;
    }

    public void setImagesIds(List<Long> imagesIds) {
        this.imagesIds = imagesIds;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }
}
