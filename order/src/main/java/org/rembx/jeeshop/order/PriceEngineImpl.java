package org.rembx.jeeshop.order;

import org.apache.commons.collections.CollectionUtils;
import org.rembx.jeeshop.catalog.DiscountFinder;
import org.rembx.jeeshop.catalog.model.CatalogPersistenceUnit;
import org.rembx.jeeshop.catalog.model.Discount;
import org.rembx.jeeshop.catalog.model.SKU;
import org.rembx.jeeshop.order.model.Order;
import org.rembx.jeeshop.order.model.OrderDiscount;
import org.rembx.jeeshop.order.model.OrderItem;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.List;

/**
 * Order price engine
 * Computes order's price and updates Order's properties
 */
public class PriceEngineImpl implements PriceEngine {

    @PersistenceContext(unitName = CatalogPersistenceUnit.NAME)
    private EntityManager entityManager;

    @Inject
    private OrderConfiguration orderConfiguration;

    @Inject
    private DiscountFinder discountFinder;

    @Inject
    private OrderFinder orderFinder;

    public PriceEngineImpl() {

    }

    public PriceEngineImpl(EntityManager entityManager, OrderConfiguration orderConfiguration,
                           OrderFinder orderFinder, DiscountFinder discountFinder) {
        this.entityManager = entityManager;
        this.orderConfiguration = orderConfiguration;
        this.orderFinder = orderFinder;
        this.discountFinder = discountFinder;
    }

    @Override
    public void computePrice(Order order) {

        if (CollectionUtils.isEmpty(order.getItems())){
            throw new IllegalStateException("Order items list is empty "+order);
        }

        Double price = 0.0;

        for (OrderItem orderItem : order.getItems()){

            SKU sku = entityManager.find(SKU.class,(orderItem).getSkuId());
            price += (sku.getPrice()*(orderItem).getQuantity());
            orderItem.setPrice(price);
        }

        final Double fixedDeliveryFee = orderConfiguration.getFixedDeliveryFee();

        price = applyEligibleDiscounts(order, price);

        if (fixedDeliveryFee != null){
            price += fixedDeliveryFee;
            order.setDeliveryFee(fixedDeliveryFee);
        }

        order.setPrice(price);

    }

    private Double applyEligibleDiscounts(Order order, Double price) {

        double originalPrice = price;

        Long userCompletedOrders = orderFinder.countUserCompletedOrders(order.getUser());

        List<Discount> userEligibleOrderDiscounts = discountFinder.findEligibleOrderDiscounts(null,userCompletedOrders);

        if (userEligibleOrderDiscounts == null) {
            return price;
        }

        if (order.getOrderDiscounts() == null){
            order.setOrderDiscounts(new HashSet<>());
        }

        for (Discount discount : userEligibleOrderDiscounts){
            price = discount.processDiscount(price, originalPrice);
            order.getOrderDiscounts().add(new OrderDiscount(discount.getId(),discount.getDiscountValue()));
        }

        return price;
    }
}
