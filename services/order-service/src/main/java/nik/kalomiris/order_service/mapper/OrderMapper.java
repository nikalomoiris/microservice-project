package nik.kalomiris.order_service.mapper;

import nik.kalomiris.order_service.domain.OrderLineItem;
import nik.kalomiris.order_service.dto.OrderLineItemsDto;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    public OrderLineItem mapToOrderLineItem(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItem.setProductId(orderLineItemsDto.getProductId());
        return orderLineItem;
    }
}
