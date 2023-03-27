package com.gbsfo.ecommerce.service.impl;

import java.util.Optional;

import javax.transaction.Transactional;

import com.gbsfo.ecommerce.controller.exception.ResourceAlreadyExistException;
import com.gbsfo.ecommerce.controller.exception.ResourceNotFoundException;
import com.gbsfo.ecommerce.domain.Order;
import com.gbsfo.ecommerce.dto.OrderDto;
import com.gbsfo.ecommerce.dto.OrderLookupPublicApiRequest;
import com.gbsfo.ecommerce.mapper.OrderMapper;
import com.gbsfo.ecommerce.repository.OrderRepository;
import com.gbsfo.ecommerce.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.util.StringUtils;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import static com.gbsfo.ecommerce.service.specification.OrderSearchSpecifications.idEquals;
import static com.gbsfo.ecommerce.service.specification.OrderSearchSpecifications.numberEquals;
import static com.gbsfo.ecommerce.service.specification.OrderSearchSpecifications.orderStatusEquals;
import static com.gbsfo.ecommerce.utils.LogStructuredArguments.orderId;

@Service
@Transactional
@Slf4j
public class OrderService implements IOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * Public API find orders request
     * Example:
     * total: 100
     * offset: 50, limit: 10
     * pages to skip = 5
     * 50 / 10 = offset / limit = 5
     */
    @Override
    public Page<Order> findOrders(OrderLookupPublicApiRequest request) {
        Specification<Order> searchSpecification = Specification
            .where(idEquals(request.getId()))
            .and(numberEquals(request.getNumber()))
            .and(orderStatusEquals(request.getOrderStatus()));

        return orderRepository.findAll(searchSpecification, PageRequest.of(request.getOffset() / request.getLimit(), request.getLimit()));
    }

    @Override
    public Order getOrderById(Long orderId) {
        log.info("Get Order attempt for {}", orderId(orderId));
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: ", orderId));
    }

    @Override
    public Optional<Order> findByNumber(String number) {
        if (StringUtils.isBlank(number)) {
            log.error("Number is blank: {}", number);
            throw new IllegalArgumentException("Number is blank: " + number);
        }
        return orderRepository.findFirstByNumberEquals(number);
    }

    @Override
    public Order createOrder(Order order) {
        if (findByNumber(order.getNumber()).isPresent()) {
            throw new ResourceAlreadyExistException("Order already exists. Can’t create new Order with same number: " + order.getNumber());
        }
        log.info("Saving order in database {}", order);

        order.getTotal_items().forEach(item -> item.setOrder(order));
        order.getTotal_payments().forEach(payment -> payment.setOrder(order));

        return orderRepository.save(order);
    }

    @Override
    public Order updateOrder(Long orderId, OrderDto orderRequest) {
        var orderInDatabase = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: ", orderId));
        var orderFromRequest = orderMapper.toEntity(orderRequest);

        log.info("Updating order in database, source = {}, target = {}", orderInDatabase, orderFromRequest);
        BeanUtils.copyProperties(orderInDatabase, orderFromRequest, "id", "number", "orderStatus");

        orderFromRequest.getTotal_items().forEach(item -> item.setOrder(orderFromRequest));
        orderFromRequest.getTotal_payments().forEach(payment -> payment.setOrder(orderFromRequest));

        return orderRepository.save(orderFromRequest);
    }

    @Override
    public void deleteOrder(Long orderId) {
        log.info("Order deleted attempt for {}", orderId(orderId));
        var order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: ", orderId));
        orderRepository.deleteById(order.getId());
    }
}