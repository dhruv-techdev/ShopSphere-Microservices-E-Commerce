package com.shopsphere.e2e.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Mirrors Angular's {@code number: '1.2-2'} pipe in the en-US locale (e.g. {@code 1,234.50}). */
public final class Amounts {

    private Amounts() {
    }

    public static String display(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(amount.setScale(2, RoundingMode.HALF_EVEN));
    }
}
