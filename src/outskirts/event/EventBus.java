package outskirts.event;

import net.jodah.typetools.TypeResolver;
import outskirts.util.CollectionUtils;
import outskirts.util.Validate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

public class EventBus {

    //          Event  Handler[]
    private Map<Class, List<Handler>> eventHandlers = new HashMap<>();


    /**
     *  for normal, just use default ArrayList for high-speed handler read/iteration.
     *  f needs dynamics register/unregister when handler is executing, can use CopyOnIterateArrayList..
     *  not use Suppler-Function-Factory because that though more convenient for init EventBus,
     *  but that'll make EventBus some little loose - misc field/setter
     */
    protected List createHandlerList() {
        return new ArrayList();
    }


    /**
     * the Mainly method of EventBus.
     * register a EventHandler as unit by a Lambda-Function(interface Consumer)
     * @param eventClass a target event's class which you want receives
     * @param function the handler
     * @param unregisterTag you can unregister this handler by using the tag to calls unregister() method
     */
    public <E extends Event> void register(Class<E> eventClass, Consumer<E> function, int priority, boolean ignoreCancelled, Object unregisterTag) {
        List<Handler> handlers = eventHandlers.get(eventClass);
        if (handlers == null) {
            eventHandlers.put(eventClass, handlers = createHandlerList());
        }

        handlers.add(new Handler(function, priority, ignoreCancelled, unregisterTag));

        //f last 2 handler had different priority
        if (handlers.size() >= 2 && handlers.get(handlers.size() - 2).priority != handlers.get(handlers.size() - 1).priority) {
            //handlers.sort(Comparator.reverseOrder()); // Arrays.sort - MargeSort alloc mem
            CollectionUtils.insertionSort(handlers, Comparator.reverseOrder());
        }
    }
    public <E extends Event> void register(Class<E> eventClass, Consumer<E> function, int priority, boolean ignoreCancelled) {
        register(eventClass, function, priority, ignoreCancelled, null);
    }
    public final <E extends Event> void register(Class<E> eventClass, Consumer<E> functionHandler) {
        register(eventClass, functionHandler, EventHandler.DEFAULT_PRIORITY, EventHandler.DEFAULT_IGNORE_CANCELLED);
    }
    public final <E extends Event> void register(Consumer<E> functionHandler) {
        // crazy powerful function..
        Class eventClass = TypeResolver.resolveRawArguments(Consumer.class, functionHandler.getClass())[0];
        register(eventClass, functionHandler, EventHandler.DEFAULT_PRIORITY, EventHandler.DEFAULT_IGNORE_CANCELLED);
    }

    // for supports static class/methods, that may have some problem about unnecessary complexity
    // e.g does static-listener using non-static-method..? instanced-listener using static-method..?
    /**
     * batched register EventHandlers in owner's each methods, witch passed following several condition:
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

                //EventHandler info
                Class eventClass = method.getParameterTypes()[0];
                int priority = annotation.priority();
                boolean ignoreCancelled = annotation.ignoreCancelled();

                method.setAccessible(true);

                Consumer function = event -> {
                    try {
                        method.invoke(owner, event);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        throw new RuntimeException("Failed to invoke Method EventHandler.", ex);
                    }
                };

                register(eventClass, function, priority, ignoreCancelled, owner);
            }
        }
    }

    public void unregister(Object functionOrUnregisterTag) {
        boolean removed = false;
        for (List<Handler> handlers : eventHandlers.values()) {
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

        List<Handler> handlers = eventHandlers.get(event.getClass());
        if (handlers == null) //if there are no handlers for this Event
            return false;

        for (Handler handler : handlers) {

            handler.invoke(event);

        }

        return (event instanceof Cancellable) && ((Cancellable)event).isCancelled();
    }


    private static class Handler implements Comparable<Handler> {
        private int priority;
        private boolean ignoreCancelled;
        private Consumer function;
        private final Object unregisterTag;  // only for unregister search

        private Handler(Consumer function, int priority, boolean ignoreCancelled, Object unregisterTag) {
            this.function = function;
            this.priority = priority;
            this.ignoreCancelled = ignoreCancelled;
            this.unregisterTag = unregisterTag;
        }

        private void invoke(Event event) {
            if (!ignoreCancelled && (event instanceof Cancellable) && ((Cancellable)event).isCancelled()) {
                return;
            }
            try
            {
                function.accept(event);
            }
            catch (Throwable t)
            {
                throw new RuntimeException("An exception occurred on EventHandler execution.", t);
            }
        }

        @Override
        public int compareTo(Handler o) {
            return this.priority - o.priority;
        }
    }
}
