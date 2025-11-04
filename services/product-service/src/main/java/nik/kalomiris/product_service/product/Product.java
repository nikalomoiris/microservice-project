package nik.kalomiris.product_service.product;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import nik.kalomiris.product_service.category.Category;
import nik.kalomiris.product_service.image.Image;

@Entity
@Table(name = "products")
public class Product {
    /**
     * Domain entity representing a product in the catalog.
     *
     * Fields include sku, name, price and relations to categories/images.
     */

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private double price;
    private String sku;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_categories",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Image> images = new ArrayList<>();

    public Product() {
        // Required by JPA: JPA uses reflection to instantiate entities,
        // so a public or protected no-argument constructor is necessary.
    }
    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public List<Image> getImages() {
        return images;
    }

    public void setImages(List<Image> images) {
        this.images = images;
    }

     public void addImage(Image image) {
        images.add(image);
        image.setProduct(this);
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

    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
}
