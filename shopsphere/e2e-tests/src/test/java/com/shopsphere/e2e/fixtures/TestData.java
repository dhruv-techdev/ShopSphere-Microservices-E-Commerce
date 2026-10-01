package com.shopsphere.e2e.fixtures;

import com.shopsphere.e2e.api.ApiException;
import com.shopsphere.e2e.api.AuthSession;
import com.shopsphere.e2e.api.Credentials;
import com.shopsphere.e2e.api.NewUser;
import com.shopsphere.e2e.api.ShippingAddress;
import com.shopsphere.e2e.api.ShopSphereApi;
import com.shopsphere.e2e.model.ProductDraft;
import com.shopsphere.e2e.support.Unique;

import java.math.BigDecimal;
import java.util.Optional;

/** Arranges accounts, products and orders through the API. Thread-safe; shared by all test classes. */
public final class TestData {

    private static final String PASSWORD = "E2e-Passw0rd!";
    private static final int ORDER_QUANTITY = 2;
    private static final int ORDER_STOCK = 50;

    private final ShopSphereApi api;
    private final Optional<Credentials> configuredAdmin;
    private Credentials admin;

    public TestData(ShopSphereApi api, Optional<Credentials> configuredAdmin) {
        this.api = api;
        this.configuredAdmin = configuredAdmin;
    }

    public record Account(Credentials credentials, AuthSession session) {

        public long userId() {
            return session.userId();
        }
    }

    public record SeededOrder(long id, long customerId, String productName, int quantity, BigDecimal unitPrice) {

        public BigDecimal total() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /** The configured admin, or a throwaway ADMIN registered once per run. */
    public synchronized Credentials admin() {
        if (admin == null) {
            admin = configuredAdmin.orElseGet(() -> register(NewUser.admin(Unique.email("admin"), PASSWORD)).credentials());
        }
        return admin;
    }

    public Account newCustomer() {
        return register(NewUser.customer(Unique.email("customer"), PASSWORD));
    }

    public long createProduct(ProductDraft draft) {
        return api.createProduct(adminSession(), draft);
    }

    /** A fresh customer orders {@value #ORDER_QUANTITY} units of a fresh, stocked product. */
    public SeededOrder placeOrder() {
        AuthSession admin = adminSession();
        ProductDraft product = ProductDraft.unique("Order Item").withStock(ORDER_STOCK);
        long productId = api.createProduct(admin, product);
        api.initInventory(admin, productId, ORDER_STOCK);

        Account customer = newCustomer();
        api.addToCart(customer.session(), productId, ORDER_QUANTITY);
        long orderId = api.placeOrder(customer.session(), ShippingAddress.toronto("E2E Customer"));
        return new SeededOrder(orderId, customer.userId(), product.name(), ORDER_QUANTITY, product.price());
    }

    private AuthSession adminSession() {
        return api.login(admin());
    }

    private Account register(NewUser user) {
        try {
            return new Account(user.credentials(), api.register(user));
        } catch (ApiException e) {
            if (e.status() == 403) {
                throw new IllegalStateException("Login was refused for a freshly registered " + user.role()
                        + ". Run the stack with docker-compose.e2e.yml (AUTH_REQUIRE_VERIFIED_EMAIL=false).", e);
            }
            throw e;
        }
    }
}
