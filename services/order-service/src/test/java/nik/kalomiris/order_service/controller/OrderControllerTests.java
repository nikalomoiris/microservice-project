package nik.kalomiris.order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import nik.kalomiris.order_service.dto.OrderLineItemsDto;
import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

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

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldCreateOrder() throws Exception {
        OrderRequest orderRequest = getOrderRequest();
        String orderRequestString = objectMapper.writeValueAsString(orderRequest);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestString))
                .andExpect(status().isCreated());

        assertEquals(1, orderRepository.findAll().size());
    }

    private OrderRequest getOrderRequest() {
        OrderRequest orderRequest = new OrderRequest();
        OrderLineItemsDto orderLineItemsDto = new OrderLineItemsDto();
        orderLineItemsDto.setSku("test-sku");
        orderLineItemsDto.setPrice(BigDecimal.TEN);
        orderLineItemsDto.setQuantity(1);
        orderRequest.setOrderLineItemsDtoList(List.of(orderLineItemsDto));
        return orderRequest;
    }
}
