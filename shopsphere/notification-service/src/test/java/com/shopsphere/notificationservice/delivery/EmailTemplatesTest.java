package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US39 — every NotificationType must have a template that renders. */
class EmailTemplatesTest {

    private static EmailRenderer renderer;

    @BeforeAll
    static void engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        renderer = new EmailRenderer(engine);
    }

    private static Map<String, Object> fullModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("subject", "Subject line");
        model.put("recipientName", "Jane");
        model.put("orderId", 42L);
        model.put("totalAmount", new BigDecimal("100.00"));
        model.put("itemCount", 2);
        model.put("amount", new BigDecimal("100.00"));
        model.put("paymentReference", "PAY-REF-1");
        model.put("reason", "Card declined");
        model.put("reasonDescription", "Payment wasn't confirmed in time.");
        model.put("cancelledAt", "Sep 26, 2026 at 22:15 UTC");
        model.put("productId", 10L);
        model.put("sellableQuantity", 2);
        model.put("threshold", 10);
        model.put("availableQuantity", 5);
        model.put("reservedQuantity", 3);
        model.put("carrier", "ShopSphere Express");
        model.put("trackingNumber", "SSX2609264K7QZ9M2PA");
        model.put("destination", "Toronto, CA");
        model.put("shippedAt", "Sep 26, 2026 at 12:00 UTC");
        model.put("deliveredAt", "Sep 28, 2026 at 15:30 UTC");
        return model;
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyTypeHasARenderableTemplate(NotificationType type) {
        String html = renderer.render(type, fullModel());

        assertThat(html)
                .contains("ShopSphere")               // shared header fragment
                .contains("Please don't reply")       // shared footer fragment
                .contains("Jane")
                .doesNotContainPattern("\\sth:[a-z-]+=");  // fully processed (no leftover th:* attributes; "width:" in CSS is fine)
    }

    @Test
    void shipmentDispatched_showsTrackingDetails() {
        String html = renderer.render(NotificationType.SHIPMENT_DISPATCHED, fullModel());

        assertThat(html)
                .contains("#42")
                .contains("SSX2609264K7QZ9M2PA")
                .contains("ShopSphere Express")
                .contains("Toronto, CA");
    }

    @Test
    void userSuppliedText_isHtmlEscaped() {
        Map<String, Object> model = fullModel();
        model.put("recipientName", "<script>alert(1)</script>");

        String html = renderer.render(NotificationType.ORDER_PLACED, model);

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
