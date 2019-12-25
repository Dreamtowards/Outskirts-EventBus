# Outskirts::EventBus

EventBus in Outskirts System



## Get start

At first, just define a Event Class (this class just for test, so not needs too seriously.. lmao)

```
public static class TestEvent extends Event {
    private String message;

    public TestEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
```

### Lambda Registration

lambda is EventHandler's original unit.

we choose a most comprehensively register method (the mainly register method, 5 param), and choose a most easy tooltype register method (1 param).

#### write a handler function as Event-Handler (Must be 1 param AND the param extends Event.class)

```
private static void onSomeEvent123(TestEvent event) {

    System.out.println(event.getMessage());
}
```

#### Mainly register method:

```
Events.EVENT_BUS.register(TestEvent.class, Test::onSomeEvent123, EventPriority.NORMAL, false, null);
```
note that Events.EVENT_BUS is a global default-eventbus, and the handler function onSomeEvent123(..) is in Test.class

this is mainly method, it have most comprehensive functions, but always not very convenience.

#### Most Use-Easy register method

```
Events.EVENT_BUS.register(Test::onSomeEvent123);
```

yeah... just a method reference, and no other thing. its will auto resolves the Event Class in the reference method, and other values all be defaults

### Method (Owner batched) Registeration

for one method handler, we just use lambda registration way just ok. but sometimes, we perfer uses multi methods in a class to handles events, and just not want register each handler.

use the Method Registration, EventBus can registers your "owner" object's all methods which 
1. the method have @EventHandler annotation 
2. method is non-static
3.method have only one param and the param is extends Event.class class

for clarity, we just clear examples above in Lambda Registration, and write examples for Method Registration

```
public Test() {
    Events.EVENT_BUS.register(this);
}

@EventHandler
private void onSomeEventOne(TestEvent event) {

    System.out.println(event.getMessage());
}

@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
private void onSomeEventTwo(TestEvent event) {

    System.out.println("Two: " + event.getMessage());
}
```
then, we had registered 2 method in the "owner" object in Test's constructor.

note that second @EventHandler had 2 properties, so Method Registration also can set "priority" and "ignoreCancelled" property. more about the properties, will explan later.

### Post the Event

we can post a Event to a EventBus, that calls all Event-Handlers which registered on the EventBus.

insert the code after register:
```
    Events.EVENT_BUS.post(new TestEvent("Test Message"));
```

when insert to `Method Registration`'s code context, after the post() executed, we will see that console had 2 line print:

```
Two: Test Message
Test Message
```

### Cancellment

### Priority
