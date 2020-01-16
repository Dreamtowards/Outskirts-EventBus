package outskirts.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {

    int DEF_PRIORITY = EventPriority.NORMAL;
    boolean DEF_IGNORE_CANCELLED = false;
    Class<?> DEF_SCHEDULER = Class.class;

    int priority() default DEF_PRIORITY;

    boolean ignoreCancelled() default DEF_IGNORE_CANCELLED;

    /**
     * which Class had { public static Scheduler getScheduler() } method.
     */
    Class<?> scheduler() default Class.class; // DEF_SCHEDULER

}
