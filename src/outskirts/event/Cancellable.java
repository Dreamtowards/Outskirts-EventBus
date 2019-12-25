package outskirts.event;

import outskirts.util.ReflectionUtils;

public interface Cancellable {

    default boolean isCancelled() {
        return ReflectionUtils.getFieldValue(Event._REF_FIELD_CANCELLED, this);
    }

    default void setCancelled(boolean cancel) {
        ReflectionUtils.setFieldValue(Event._REF_FIELD_CANCELLED, this, cancel);
    }

}
