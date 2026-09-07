package com.cinecraft.compat;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

final class TemporaryBooleanStateTest {
    @Test
    void restoresTheOriginalHudValueAcrossConfigurationChanges() {
        AtomicBoolean hidden = new AtomicBoolean(true);
        TemporaryBooleanState state = new TemporaryBooleanState();
        state.apply(false, hidden::get, hidden::set);
        state.apply(true, hidden::get, hidden::set);
        state.apply(false, hidden::get, hidden::set);
        state.restore(hidden::get, hidden::set);
        assertTrue(hidden.get());
    }

    @Test
    void neverReacquiresOrRestoresAfterAnotherOwnerChangesTheOption() {
        AtomicBoolean hidden = new AtomicBoolean(true);
        TemporaryBooleanState state = new TemporaryBooleanState();
        state.apply(false, hidden::get, hidden::set);
        hidden.set(true);
        state.apply(false, hidden::get, hidden::set);
        assertTrue(hidden.get());
        hidden.set(false);
        state.restore(hidden::get, hidden::set);
        assertFalse(hidden.get());
    }
}
