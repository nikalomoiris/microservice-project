package nik.kalomiris.order_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import nik.kalomiris.order_service.dto.ProductInfoDto;
import nik.kalomiris.order_service.dto.ProductPrice;
import nik.kalomiris.order_service.exception.ProductValidationException;

/**
 * Client for communicating with the Product Service.
 * 
 * Fetches product pricing information to validate orders before creation.
 * This ensures the order-service uses current, verified prices from the
 * product catalog rather than trusting prices sent by the UI.
 */
@Service
public class ProductServiceClient {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;

    public ProductServiceClient(
            RestTemplate restTemplate,
            @Value("${product.service.url}") String productServiceUrl) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
    }

    /**
     * Fetches current price and SKU for a product.
     * 
     * @param productId the product identifier
     * @return ProductPrice containing verified price and SKU
     * @throws ProductValidationException if product not found or service
     *                                    unavailable
     */
    public ProductPrice getProduct(Long productId) {
        String url = productServiceUrl + "/api/products/" + productId;

        try {
            ResponseEntity<ProductInfoDto> response = restTemplate.getForEntity(url, ProductInfoDto.class);

            ProductInfoDto productInfo = response.getBody();

            if (productInfo == null || productInfo.getPrice() == null || productInfo.getSku() == null) {
                throw new ProductValidationException(
                        "Cannot validate product price for productId=" + productId +
                                ": Invalid product data received");
            }

            return new ProductPrice(productInfo.getPrice(), productInfo.getSku());

        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductValidationException(
                    "Cannot validate product price for productId=" + productId +
                            ": Product not found");

        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ProductValidationException(
                    "Cannot validate product price for productId=" + productId +
                            ": Product-service unavailable");
        }
    }
}
