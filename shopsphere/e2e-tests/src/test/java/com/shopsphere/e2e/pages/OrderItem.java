package com.shopsphere.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** A line in the order detail "Items" table. */
public record OrderItem(String productName, int quantity, String lineTotal) {

    static OrderItem from(WebElement row) {
        return new OrderItem(
                row.findElement(By.cssSelector("td:nth-child(1) .strong")).getText().trim(),
                Integer.parseInt(row.findElement(By.cssSelector("td:nth-child(3)")).getText().trim()),
                row.findElement(By.cssSelector("td:nth-child(4)")).getText().trim());
    }
}
