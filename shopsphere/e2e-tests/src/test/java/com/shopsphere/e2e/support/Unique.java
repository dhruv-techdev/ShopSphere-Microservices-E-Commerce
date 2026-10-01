package com.shopsphere.e2e.support;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Collision-free names so runs never depend on (or clash with) data from earlier runs. */
public final class Unique {

    private static final String RUN = Long.toString(System.currentTimeMillis(), 36)
            + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36), 36);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private Unique() {
    }

    /** e.g. {@code mg3k2x9q4-07}: same prefix for the whole run, increasing suffix. */
    public static String token() {
        return RUN + "-" + String.format(Locale.ROOT, "%02d", SEQUENCE.incrementAndGet());
    }

    public static String email(String role) {
        return "e2e-" + role + "-" + token() + "@shopsphere.test";
    }
}
