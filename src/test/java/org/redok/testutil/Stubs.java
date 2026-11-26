package org.redok.testutil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Заглушки интерфейсов JDA на {@link Proxy}, без Mockito. Ответы задаются парами «имя метода, значение»:
 * значение возвращается как есть, {@link RuntimeException} бросается, {@link Answer} вычисляется.
 * Fluent-методы вроде delay() и setContent() возвращают саму заглушку, методы с интерфейсным
 * результатом — новую заглушку, остальные — 0, false или null. Все вызовы записываются в {@link #calls()}.
 */
public final class Stubs {

    @FunctionalInterface
    public interface Answer {
        Object answer(Object[] args) throws Throwable;
    }

    private final List<String> calls = new CopyOnWriteArrayList<>();

    public <T> T stub(Class<T> type, Object... answers) {
        return type.cast(proxy(new Class<?>[]{type}, answers));
    }

    /** Заглушка сразу нескольких интерфейсов: например, канал, который и MessageChannel, и GuildChannel. */
    public Object proxy(Class<?>[] types, Object... answers) {
        Map<String, Object> byName = new HashMap<>();
        for (int i = 0; i < answers.length; i += 2) {
            byName.put((String) answers[i], answers[i + 1]);
        }
        String name = types[0].getSimpleName();
        InvocationHandler handler = (proxy, method, args) -> {
            Object[] safeArgs = args == null ? new Object[0] : args;
            String methodName = method.getName();
            calls.add(name + "." + methodName + Arrays.toString(safeArgs));
            if (byName.containsKey(methodName)) {
                Object value = byName.get(methodName);
                if (value instanceof Answer answer) {
                    return answer.answer(safeArgs);
                }
                if (value instanceof RuntimeException e) {
                    throw e;
                }
                return value;
            }
            switch (methodName) {
                case "toString" -> {
                    return name + "Stub";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == safeArgs[0];
                }
                default -> {
                }
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.isInstance(proxy)) {
                return proxy;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType.isInterface()) {
                return stub(returnType);
            }
            return null;
        };
        return Proxy.newProxyInstance(Stubs.class.getClassLoader(), types, handler);
    }

    /** Все вызовы всех заглушек в формате {@code Тип.метод[аргументы]}. */
    public List<String> calls() {
        return calls;
    }

    public boolean called(String fragment) {
        return calls.stream().anyMatch(call -> call.contains(fragment));
    }
}
