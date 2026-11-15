package com.DistributedPaymentGateway.payment_service.mapper;

import com.DistributedPaymentGateway.payment_service.dto.response.OrderResponse;
import com.DistributedPaymentGateway.payment_service.entity.OrderRecord;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {
    OrderResponse toOrderResponse(OrderRecord orderRecord);
}
