package com.shopsphere.e2e.pages;

import java.util.Arrays;

/** An enum shown in the UI by a human label (chips, status text). */
public interface Labelled {

    String label();

    static <E extends Enum<E> & Labelled> E fromLabel(Class<E> type, String label) {
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown " + type.getSimpleName() + " label '" + label + "'"));
    }
}
