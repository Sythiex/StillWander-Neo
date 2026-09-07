package com.cinecraft.compat;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Restores a temporary option only while its current value is still the one we wrote. */
public final class TemporaryBooleanState {
    private boolean acquired;
    private boolean owned;
    private boolean previous;
    private boolean written;

    public void apply(boolean desired, BooleanSupplier read, Consumer<Boolean> write) {
        boolean current = read.getAsBoolean();
        if (!acquired) {
            previous = current;
            written = current;
            acquired = true;
            owned = true;
        }
        if (current != written) owned = false;
        if (!owned) return;
        if (current != desired) write.accept(desired);
        written = desired;
    }

    public void restore(BooleanSupplier read, Consumer<Boolean> write) {
        if (acquired && owned && read.getAsBoolean() == written) write.accept(previous);
        acquired = false;
        owned = false;
    }
}
