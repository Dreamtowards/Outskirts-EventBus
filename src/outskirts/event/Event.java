package outskirts.event;

import outskirts.util.ReflectionUtils;

import java.lang.reflect.Field;

public abstract class Event {

    static final Field _REF_FIELD_CANCELLED = ReflectionUtils.getField(Event.class, "cancelled");

    private boolean cancelled;


}
