package org.redok.listeners;

public class EventControllerListenerFactory {

    private static EventControllerListener eventControllerListener;

    public static EventControllerListener getEventControllerListener() {
        if (eventControllerListener == null) {
            eventControllerListener = new EventControllerListener();
        }
        return eventControllerListener;
    }
}
