package com.cinecraft.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Read-only optional-mod query. A broken installed API yields the camera conservatively. */
final class OptionalActivityProbe {
    enum State { INACTIVE, ACTIVE, UNAVAILABLE }

    private final BooleanSupplier installed;
    private final String className;
    private final String instanceField;
    private final String methodName;
    private final Consumer<Throwable> failure;
    private Field instance;
    private Method method;
    private boolean failed;

    OptionalActivityProbe(BooleanSupplier installed, String className, String instanceField,
                          String methodName, Consumer<Throwable> failure) {
        this.installed = installed;
        this.className = className;
        this.instanceField = instanceField;
        this.methodName = methodName;
        this.failure = failure;
    }

    State query() {
        if (!installed.getAsBoolean()) return State.INACTIVE;
        if (failed) return State.UNAVAILABLE;
        try {
            if (method == null) {
                Class<?> type = Class.forName(className);
                method = type.getMethod(methodName);
                if (instanceField != null) instance = type.getField(instanceField);
            }
            Object receiver = instance == null ? null : instance.get(null);
            if (instance != null && receiver == null) return State.INACTIVE;
            Object result = method.invoke(receiver);
            boolean active = method.getReturnType() == boolean.class ? (Boolean) result : result != null;
            return active ? State.ACTIVE : State.INACTIVE;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failed = true;
            failure.accept(exception);
            return State.UNAVAILABLE;
        }
    }
}
