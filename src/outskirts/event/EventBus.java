package outskirts.event;

import net.jodah.typetools.TypeResolver;
import outskirts.util.CollectionUtils;
import outskirts.util.ReflectionUtils;
import outskirts.util.Validate;
import outskirts.util.concurrent.Scheduler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

public class EventBus {

    private static final Comparator<Handler> COMP_HANDLER_PRIORITY_DESC = Collections.reverseOrder(Comparator.comparingInt(Handler::priority));

    //          Event  Handler[]
    private Map<Class, List<Handler>> handlersMap = new HashMap<>();


    /**
     *  for normal, just use default ArrayList for high-speed handler read/iteration.
     *  f needs dynamics register/unregister when handler is executing, can use CopyOnIterateArrayList..
     *  not use Suppler-Function-Factory because that though more convenient for init EventBus,
     *  but that'll make EventBus some little loose - misc field/setter. but inheritment
     */
    protected List createHandlerList() {
        return new ArrayList();
    }

    /**
     * the Mainly method of EventBus.
     * register a EventHandler as unit by a Lambda-Function(interface Consumer)
     * @param eventClass a target event's class which you want receives
     * @param function the handler
     */
    public <E extends Event> Handler register(Class<E> eventClass, Consumer<E> function) {
        List<Handler> handlers = handlersMap.get(eventClass);
        if (handlers == null) {
            handlersMap.put(eventClass, handlers = createHandlerList());
        }

        Handler handler = new Handler(function);
        handlers.add(handler);

        //f last 2 handler had different priority
        if (handlers.size() >= 2 && handlers.get(handlers.size() - 2).priority != handlers.get(handlers.size() - 1).priority) {
            //handlers.sort(Comparator.reverseOrder()); // Arrays.sort - MargeSort alloc mem
            CollectionUtils.insertionSort(handlers, COMP_HANDLER_PRIORITY_DESC);
        }
        return handler;
    }
    public final <E extends Event> Handler register(Consumer<E> function) {
        // crazy powerful function..
        Class eventClass = TypeResolver.resolveRawArguments(Consumer.class, function.getClass())[0];
        return register(eventClass, function);
    }

    // for supports static class/methods, that may have some problem about unnecessary complexity
    // e.g does static-listener using non-static-method..? instanced-listener using static-method..?
    // needs filter ..? like a handlers-impl-class's some handlers for diff EventBus
    /**
     * batched register EventHandlers in owner's each methods, which passed following several condition:
     * 1.method have @EventHandler annotation
     * 2.method is non-static
     * 3.method have only one param and the param is extends Event.class class
     * that method will be register. When event happen in its EventBus, that EventHandler(method) will be call
     *
     * EventHandler(method) support not public(you can public/private/protected/friendly)
     */
    public final void register(Object owner) {
        Validate.isTrue(!(owner instanceof Class), "Class(Static-Listener) is Unsupported.");
        for (Method method : owner.getClass().getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation != null) {
                Validate.isTrue(!Modifier.isStatic(method.getModifiers()), "static method is unsupported. (method: %s)", method.getName());
                Validate.isTrue(method.getParameterCount() == 1 && Event.class.isAssignableFrom(method.getParameterTypes()[0]),
                        "EventHandler method require only-one <? extends Event> param (method: %s)", method.getName());

                // EventHandler Info
                Class eventClass = method.getParameterTypes()[0];
                int priority = annotation.priority();
                boolean ignoreCancelled = annotation.ignoreCancelled();
                Scheduler scheduler = resolveScheduler(annotation);


                method.setAccessible(true);

                Consumer function = event -> {
                    try {
                        method.invoke(owner, event);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        throw new RuntimeException("Failed to invoke this Method EventHandler.", ex);
                    }
                };

                register(eventClass, function)
                        .priority(priority)
                        .ignoreCancelled(ignoreCancelled)
                        .unregisterTag(owner)
                        .scheduler(scheduler);
            }
        }
    }
    private static Scheduler resolveScheduler(EventHandler annotation) {
        if (annotation.scheduler() == EventHandler.DEF_SCHEDULER)
            return null;
        try {
            return (Scheduler) annotation.scheduler().getMethod("getScheduler").invoke(null);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
            throw new RuntimeException("Failed to resolve getScheduler().", ex);
        }
    }

    /**
     * @throws IllegalStateException when nothing been unregistered
     */
    public void unregister(Object functionOrUnregisterTag) {
        boolean removed = false;
        for (List<Handler> handlers : handlersMap.values()) {
            if (handlers.removeIf(handler -> handler.function == functionOrUnregisterTag || handler.unregisterTag == functionOrUnregisterTag))
                removed = true;
        }
        if (!removed) {
            throw new IllegalStateException("Failed to unregister: not found an EventHandler that matches the function/unregisterTag ("+functionOrUnregisterTag.getClass()+").");
        }
    }

    /**
     * perform all EventHandler(s) registered on this EventBus that typeof the Event
     * @return if true, the event has be cancelled. (only possible return true when the Event implements Cancellable)
     */
    public boolean post(Event event) {
        List<Handler> handlers = handlersMap.get(event.getClass());
        if (handlers == null) // quick exit. if there are no handlers for this Event
            return false;

        for (Handler handler : handlers)
        {
            handler.invoke(event);
        }

        return (event instanceof Cancellable) && ((Cancellable)event).isCancelled();
    }


    public static final class Handler {

        private final Consumer function; // the handler execution function. Consumer<? extends Event>
        private int priority = EventHandler.DEF_PRIORITY; // bigger number, higher priority
        private boolean ignoreCancelled = EventHandler.DEF_IGNORE_CANCELLED; // if true, the handler'll receives cancelled events.

        private Scheduler scheduler = null; // if non-null, the handler'll be perform postpone/inside the scheduler thread.
        private Object unregisterTag = null;  // only for unregister search. you can unregister this handler by using the tag object to calls unregister() method

        private Handler(Consumer function) {
            this.function = function;
        }

        private void invoke(Event event) {
            if (!ignoreCancelled && (event instanceof Cancellable) && ((Cancellable)event).isCancelled()) {
                return;
            }
            if (scheduler == null || scheduler.inSchedulerThread())
            {
                doInvoke(event);
            }
            else
            {
                scheduler.addScheduledTask(() -> doInvoke(event));
            }
        }

        private void doInvoke(Event event) {
            try
            {
                function.accept(event);
            }
            catch (Throwable t)
            {
                throw new RuntimeException("An exception occurred on EventHandler execution.", t);
            }
        }

        public int priority() {
            return priority;
        }

        public Handler priority(int priority) {
            this.priority = priority;
            return this;
        }
        public Handler ignoreCancelled(boolean ignoreCancelled) {
            this.ignoreCancelled = ignoreCancelled;
            return this;
        }
        public Handler unregisterTag(Object unregisterTag) {
            this.unregisterTag = unregisterTag;
            return this;
        }
        public Handler scheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }
    }
}
