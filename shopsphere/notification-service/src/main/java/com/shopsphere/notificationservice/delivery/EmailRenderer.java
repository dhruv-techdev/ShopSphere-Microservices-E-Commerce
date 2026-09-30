package com.shopsphere.notificationservice.delivery;

import com.shopsphere.notificationservice.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/** US39 — renders templates/email/{type}.html with the notification's model. */
@Component
@RequiredArgsConstructor
public class EmailRenderer {

    private final ITemplateEngine templateEngine;

    public String render(NotificationType type, Map<String, Object> model) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariables(model);
        return templateEngine.process(type.templateName(), context);
    }
}
