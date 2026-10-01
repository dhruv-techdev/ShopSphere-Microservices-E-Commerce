package com.shopsphere.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Optional;

/** One row of the admin orders table, as displayed. */
public record OrderRow(
        long id,
        long customerId,
        OrderStatus status,
        PaymentState payment,
        String total,
        Optional<String> trackingNumber) {

    static final By LINK = By.cssSelector("td.mat-column-id a.cell-link");
    private static final String NO_VALUE = "—";

    static OrderRow from(WebElement row) {
        String tracking = cell(row, "trackingNumber");
        return new OrderRow(
                idOf(row),
                hashNumber(cell(row, "userId")),
                OrderStatus.fromLabel(cell(row, "status")),
                PaymentState.fromLabel(cell(row, "payment")),
                cell(row, "totalAmount"),
                NO_VALUE.equals(tracking) ? Optional.empty() : Optional.of(tracking));
    }

    static long idOf(WebElement row) {
        return hashNumber(row.findElement(LINK).getText());
    }

    /** "#42" → 42 */
    private static long hashNumber(String text) {
        return Long.parseLong(text.trim().replaceFirst("^#", ""));
    }

    private static String cell(WebElement row, String column) {
        return row.findElement(By.cssSelector("td.mat-column-" + column)).getText().trim();
    }
}
