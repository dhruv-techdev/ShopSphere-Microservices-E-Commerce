package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountEmailTemplatesTest {

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

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"EMAIL_VERIFICATION", "PASSWORD_RESET"})
    void accountEmails_renderActionButtonWithTheLink(NotificationType type) {
        String link = "https://shop.test/some-page?token=AbC-123_xyz";

        String html = renderer.render(type, Map.of(
                "subject", "Subject",
                "recipientName", "Jane",
                "actionUrl", link,
                "expiresAt", "Sep 30, 2026 at 12:00 UTC"));

        assertThat(html)
                .contains("href=\"" + link + "\"")
                .contains("Sep 30, 2026 at 12:00 UTC")
                .contains("Jane")
                .contains("Please don't reply");
    }
}
