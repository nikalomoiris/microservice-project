package nik.kalomiris.order_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import nik.kalomiris.order_service.dto.OrderLineItemsDto;
import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.dto.ProductPrice;
import nik.kalomiris.order_service.repository.OrderRepository;
import nik.kalomiris.order_service.service.ProductServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import nik.kalomiris.logging_client.LogPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @SuppressWarnings("removal")
    @MockBean
    private RabbitTemplate rabbitTemplate;

    @SuppressWarnings("removal")
    @MockBean
    private LogPublisher logPublisher;

    @MockBean
    private ProductServiceClient productServiceClient;

    @Test
    void shouldCreateOrder() throws Exception {
        // Mock product-service client to return a fixed price
        when(productServiceClient.getProduct(any(Long.class)))
                .thenReturn(new ProductPrice(new BigDecimal("29.99"), "TEST-SKU"));

        OrderRequest orderRequest = getOrderRequest();
        String orderRequestString = objectMapper.writeValueAsString(
                orderRequest);

        mockMvc
                .perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(orderRequestString))
                .andExpect(status().isCreated());

        assertEquals(1, orderRepository.findAll().size());
    }

    private OrderRequest getOrderRequest() {
        OrderRequest orderRequest = new OrderRequest();
        OrderLineItemsDto orderLineItemsDto = new OrderLineItemsDto();
        orderLineItemsDto.setProductId(1L);
        orderLineItemsDto.setQuantity(1);
        orderRequest.setOrderLineItemsDtoList(List.of(orderLineItemsDto));
        return orderRequest;
    }
}
