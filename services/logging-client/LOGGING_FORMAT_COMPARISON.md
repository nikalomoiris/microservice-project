# Logging Format Comparison

## Before: Plain String Format

```
Product created: sku=ABC-123 id=42
Image added to product 1 url=/images/product-1.jpg
Products retrieved
Product updated: id=5
Product deleted: id=3
```

**Limitations:**
- No standardized structure
- Difficult to parse and query
- No metadata for filtering
- No timestamp or service information
- Cannot include distributed tracing information

---

## After: Structured JSON Format

```json
{
  "timestamp": "2025-10-23T22:38:10.123456Z",
  "level": "INFO",
  "service": "product-service",
  "message": "Product created",
  "logger": "nik.kalomiris.product_service.product.ProductService",
  "thread": "http-nio-8080-exec-1",
  "traceId": "trace-abc123",
  "spanId": "span-def456",
  "metadata": {
    "sku": "ABC-123",
    "productId": "42"
  }
}
```

```json
{
  "timestamp": "2025-10-23T22:38:15.789012Z",
  "level": "INFO",
  "service": "product-service",
  "message": "Image added to product",
  "logger": "nik.kalomiris.product_service.product.ProductService",
  "thread": "http-nio-8080-exec-2",
  "metadata": {
    "productId": "1",
    "imageUrl": "/images/product-1.jpg"
  }
}
```

```json
{
  "timestamp": "2025-10-23T22:38:20.345678Z",
  "level": "INFO",
  "service": "product-service",
  "message": "Products retrieved",
  "logger": "nik.kalomiris.product_service.product.ProductService",
  "thread": "http-nio-8080-exec-3",
  "metadata": {
    "categoryName": "Electronics"
  }
}
```

**Benefits:**
- ✅ Standardized structure across all services
- ✅ Easy to parse, query, and analyze
- ✅ Rich metadata for filtering and aggregation
- ✅ Automatic timestamp and thread information
- ✅ Support for distributed tracing (traceId, spanId)
- ✅ Better integration with log aggregation tools (ELK, Splunk, etc.)
- ✅ Consistent field names across microservices
- ✅ Optional fields are excluded from JSON (clean output)
