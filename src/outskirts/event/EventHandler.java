package outskirts.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {

    int DEFAULT_PRIORITY = EventPriority.NORMAL;
    boolean DEFAULT_IGNORE_CANCELLED = false;

    int priority() default DEFAULT_PRIORITY;

    boolean ignoreCancelled() default DEFAULT_IGNORE_CANCELLED;

}
