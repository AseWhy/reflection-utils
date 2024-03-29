package io.github.asewhy;


import io.github.asewhy.support.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"rawtypes", "unused"})
public class ReflectionUtils {
    private final static Pattern PROXY_NAME_PATTERN = Pattern.compile("\\$(.+)(:?Proxy|EnhancerBySpringCGLIB)\\$(.*)$");

    /**
     * Быстрый поиск по карте, ключом в которой является класс, метод учитывает наследование классов. Если
     * искомого класса нет, то метод выдаст наиболее близкого наследника этого класса в карте. После нахождения значения,
     * значение будет добавлено в карту как.
     *
     * @param input входящая карта с классами
     * @param key ключ для поиска
     * @param <T> тип значений карты
     * @return значение или null если не нашел (так-же будет кеширован null на текущий ключ)
     */
    public static <T> @Nullable T findOnClassMapCache(@NotNull Map<Class<?>, T> input, Class<?> key) {
        if(input.containsKey(key)) {
            return input.get(key);
        }

        var found = new HashSet<Class<?>>();

        for (var current: input.entrySet()) {
            var parent = current.getKey();

            if(parent.isAssignableFrom(key)) {
                found.add(current.getKey());
            }
        }

        if(found.size() > 0) {
            var nearest = getBoundNearestClass(key, found);
            var resultValue = input.get(nearest);
            input.put(key, resultValue);
            return resultValue;
        }

        input.put(key, null);

        return null;
    }

    /**
     * Быстрый поиск по карте, ключом в которой является класс, метод учитывает наследование классов. Если
     * искомого класса нет, то метод выдаст наиболее близкого наследника этого класса в карте
     *
     * @param input входящая карта с классами
     * @param key ключ для поиска
     * @param <T> тип значений карты
     * @return значение или null если не нашел (так-же будет кеширован null на текущий ключ)
     */
    public static <T> @Nullable T findOnClassMap(@NotNull Map<Class<?>, T> input, Class<?> key) {
        return findOnClassMap(input, key, Set.of(Object.class));
    }

    /**
     * Быстрый поиск по карте, ключом в которой является класс, метод учитывает наследование классов. Если
     * искомого класса нет, то метод выдаст наиболее близкого наследника этого класса в карте
     *
     * @param input входящая карта с классами
     * @param key ключ для поиска
     * @param <T> тип значений карты
     * @return значение или null если не нашел (так-же будет кеширован null на текущий ключ)
     */
    public static <T> @Nullable T findOnClassMap(@NotNull Map<Class<?>, T> input, Class<?> key, Set<Class<?>> excludes) {
        if(input.containsKey(key)) {
            return input.get(key);
        }

        var found = new HashSet<Class<?>>();

        for (var current: input.entrySet()) {
            var parent = current.getKey();

            if(parent.isAssignableFrom(key) && !excludes.contains(parent)) {
                found.add(current.getKey());
            }
        }

        if(found.size() > 0) {
            var nearest = getBoundNearestClass(key, found);

            return input.get(nearest);
        }

        return null;
    }

    /**
     * Получить ближайший наследный класс из списка переданных классов
     *
     * @param find класс эталон
     * @param list класс для поиска
     * @return ближайший наследник из списка
     */
    public static Class<?> getBoundNearestClass(Class<?> find, @NotNull Collection<Class<?>> list) {
        return list.stream().min(Comparator.comparingInt(a -> getParentDistance(a, find))).orElse(null);
    }

    /**
     * Получить дистанцию наследования до родительского класса
     *
     * @param child дочерний класс
     * @param parent родительский класс, до которого измеряется расстояние
     * @return дистанция от 0
     */
    private static Integer getParentDistance(Class<?> child, Class<?> parent) {
        var distance = 0;
        var current = new HashSet<Class<?>>();

        current.add(parent);

        while(current.size() != 0) {
            if(current.contains(child)) {
                break;
            }

            var newSet = new HashSet<Class<?>>();

            for(var sub: current) {
                var superclass = sub.getSuperclass();

                if(superclass != null) {
                    newSet.add(superclass);
                }

                newSet.addAll(List.of(sub.getInterfaces()));
            }

            current = newSet;

            distance++;
        }

        return distance;
    }

    /**
     * Пропустить анонимный класс, если он есть, и получить элемент ниже по дереву зависимостей
     *
     * @param clazz класс для пропуска анонимного класса
     * @param <T> типа класса
     * @return класс который нам нужен
     */
    public static <T> Class<? super T> skipAnonClasses(Class<T> clazz) {
        //
        // У анонимного или прокси класса есть супер класс с нужным нам типом.
        //
        if(isProxyOrProtectedClass(clazz)) {
            return clazz.getSuperclass();
        }

        //
        // Если нет, то возвращаем текущий класс
        //
        return clazz;
    }

    /**
     * Проверяет, является ли класс clazz прокси классом или приватным классом
     *
     * @param clazz класс для проверки
     * @return true если является
     */
    public static @NotNull Boolean isProxyOrProtectedClass(@NotNull Class<?> clazz) {
        var name = clazz.getName();

        if (PROXY_NAME_PATTERN.matcher(name).find()) {
            return true;
        }

        return clazz.getSimpleName().equals("") || Proxy.isProxyClass(clazz);
    }

    /**
     * Получает поле идентификатор в целевом классе.
     *
     * @param clazz класс для поиска идентификатора
     * @return найденное поле-идентификатор
     */
    public static Field findTypeId(Class<?> clazz) {
        var fields = scanFields(clazz, Set.of(Object.class));
        var idField = (Field) null;

        for(var field: fields) {
            if(field.getName().equals("id")) {
                idField = field;
            }

            if(field.getAnnotation(Identifier.class) != null) {
                return field;
            }
        }

        return idField;
    }

    /**
     * Получить первый generic параметр из типа
     *
     * @param genericsParams тип из которого нужно получить параметр
     * @param index индекс параметра для получения
     * @return generic тип или nell
     */
    public static @Nullable Type findXGenericType(@NotNull Type genericsParams, Integer index) {
        if(genericsParams instanceof ParameterizedType) {
            var pt = (ParameterizedType) genericsParams;
            var generics = pt.getActualTypeArguments();

            if(generics.length > index) {
                return generics[index];
            }
        }

        return null;
    }

    /**
     * Получить первый generic параметр из типа
     *
     * @param genericsParams тип из которого нужно получить параметр
     * @param index индекс параметра для получения
     * @return generic тип или nell
     */
    public static @Nullable Class<?> findXGeneric(@NotNull Type genericsParams, Integer index) {
        if(genericsParams instanceof ParameterizedType) {
            var pt = (ParameterizedType) genericsParams;
            var generics = pt.getActualTypeArguments();

            if(generics.length > index) {
                var generic = generics[index];

                if(generic instanceof Class<?>) {
                    return (Class<?>) generic;
                } else if (generic instanceof ParameterizedType) {
                    var c = (ParameterizedType) generic;
                    var type = c.getRawType();

                    if (type instanceof Class<?>) {
                        return (Class<?>) type;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Получить первый generic параметр у метода
     *
     * @param from метод, generic значение которого нужно получить
     * @param index индекс параметра для получения
     * @return generic тип или nell
     */
    public static @Nullable Class<?> findXGeneric(@NotNull Method from, Integer index) {
        return findXGeneric(from.getGenericReturnType(), index);
    }

    /**
     * Получить первый generic параметр у поля
     *
     * @param from поле, generic значение которого нужно получить
     * @param index индекс параметра для получения
     * @return generic тип или nell
     */
    public static @Nullable Class<?> findXGeneric(@NotNull Field from, Integer index) {
        return findXGeneric(from.getGenericType(), index);
    }

    /**
     * Получить первый generic параметр у класса
     *
     * @param from класс из супер класса, которого нужно получить generic
     * @param index индекс параметра для получения
     * @return generic тип или null
     */
    public static @Nullable Class<?> findXGeneric(@NotNull Class<?> from, Integer index) {
        return findXGeneric(from.getGenericSuperclass(), index);
    }

    /**
     * Получить первый generic параметр у метода
     *
     * @param from метод, generic значение которого нужно получить
     * @return generic тип или nell
     */
    public static @Nullable Class<?> findXGeneric(@NotNull Method from) {
        return findXGeneric(from.getGenericReturnType(), 0);
    }

    /**
     * Получить первый generic параметр у поля
     *
     * @param from поле, generic значение которого нужно получить
     * @return generic тип или nell
     */
    public static @Nullable Class<?> findXGeneric(@NotNull Field from) {
        return findXGeneric(from.getGenericType(), 0);
    }

    /**
     * Получить первый generic параметр у класса
     *
     * @param from класс из супер класса, которого нужно получить generic
     * @return generic тип или null
     */
    public static @Nullable Class<?> findXGeneric(@NotNull Class<?> from) {
        return findXGeneric(from.getGenericSuperclass(), 0);
    }

    /**
     * Безопасный вызов метода
     *
     * @param method метод
     * @param caller экземпляр класса метод которого вызываем
     * @param args аргументы для вызова
     * @return полученное значение
     */
    public static Object safeInvoke(@NotNull Method method, Object caller, Object ...args) {
        try {
            if(!method.canAccess(caller)) {
                method.setAccessible(true);
            }

            return method.invoke(caller, args);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Argument type mismatch exception on " + paramsToString(objectsToClass(args)) + " and " + paramsToString(method.getParameterTypes()));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Преобразовать список объектов в список классов этих объектов
     *
     * @param args список объектов
     * @return строка со строковыми значениями
     */
    public static Class<?> @NotNull[] objectsToClass(Object[] args) {
        return Arrays.stream(args).map(e -> e != null ? e.getClass() : null).toArray(Class<?>[]::new);
    }

    /**
     * Преобразовать параметры в строку
     *
     * @param args список параметров
     * @return строка со строковыми значениями
     */
    public static String paramsToString(Object[] args) {
        return Stream.of(args).map(String::valueOf).collect(Collectors.joining(", "));
    }

    /**
     * Безопасно устанавливает значение полю
     *
     * @param field поле
     * @param caller экземпляр класса поле которого изменяем
     */
    public static void safeSet(@NotNull Field field, Object caller, Object set) {
        try {
            if(!field.canAccess(caller)) {
                field.setAccessible(true);
            }

            field.set(caller, set);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Безопасно создать экземпляр переданного класса, безопасно
     *
     * @param clazz класс для создания
     * @param args аргументы конструктора
     * @return экземпляр класса
     */
    public static <T> @NotNull T safeInstance(@NotNull Class<T> clazz, Object... args) {
        try {
            return clazz.getConstructor(Arrays.stream(args)
                .map(e -> e != null ? e.getClass() : null)
                .toArray(Class[]::new))
            .newInstance(args);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Безопасный доступ к полю
     *
     * @param field поле
     * @param caller экземпляр класса поле которого получаем
     * @return полученное значение
     */
    public static Object safeAccess(@NotNull Field field, Object caller) {
        try {
            if(!field.canAccess(caller)) {
                field.setAccessible(true);
            }

            return field.get(caller);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Сканирует класс, получая от туда все методы и поля, из него и подклассов
     *
     * @param clazz целевой класс
     * @param explode исключения в дереве
     * @return поля и методы класса и супер класса
     */
    public static @NotNull List<AccessibleObject> scan(Class<?> clazz, @NotNull Set<Class<?>> explode) {
        var fields = new ArrayList<AccessibleObject>();

        while(clazz != null) {
            if(explode.contains(clazz)) {
                break;
            }

            var interfaces = clazz.getInterfaces();

            fields.addAll(List.of(clazz.getDeclaredFields()));
            fields.addAll(List.of(clazz.getDeclaredMethods()));

            for(var current: interfaces) {
                fields.addAll(scan(current, explode));
            }

            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    /**
     * Сканирует методы класса, получая методы, из него и подклассов
     *
     * @param clazz целевой класс
     * @param explode исключения в дереве
     * @return методы класса и супер класса
     */
    public static @NotNull List<Method> scanMethods(Class<?> clazz, @NotNull Set<Class<?>> explode) {
        var fields = new ArrayList<Method>();

        while(clazz != null) {
            if(explode.contains(clazz)) {
                break;
            }

            var interfaces = clazz.getInterfaces();

            fields.addAll(List.of(clazz.getDeclaredMethods()));

            for(var current: interfaces) {
                fields.addAll(scanMethods(current, explode));
            }

            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    /**
     * Сканирует поля класса, получая поля, из него и подклассов
     *
     * @param clazz целевой класс
     * @param explode исключения в дереве
     * @return поля класса и супер класса
     */
    public static @NotNull List<Field> scanFields(Class<?> clazz, @NotNull Set<Class<?>> explode) {
        var fields = new ArrayList<Field>();

        while(clazz != null) {
            if(explode.contains(clazz)) {
                break;
            }

            var interfaces = clazz.getInterfaces();

            fields.addAll(List.of(clazz.getDeclaredFields()));

            for(var current: interfaces) {
                fields.addAll(scanFields(current, explode));
            }

            clazz = clazz.getSuperclass();
        }

        return fields;
    }


    /**
     * Сканирует класс, получая от туда все методы и поля, из него и подклассов
     *
     * @param clazz целевой класс
     * @param explode исключения в дереве
     * @return поля и методы класса и супер класса
     */
    public static @NotNull Map<String, AccessibleObject> scanToMap(Class<?> clazz, @NotNull Set<Class<?>> explode) {
        var fields = new HashMap<String, AccessibleObject>();

        while(clazz != null) {
            if(explode.contains(clazz)) {
                break;
            }

            var interfaces = clazz.getInterfaces();

            for(var field: clazz.getDeclaredFields()) {
                fields.put(field.getName(), field);
            }

            for(var method: clazz.getDeclaredMethods()) {
                fields.put(method.getName(), method);
            }

            for(var current: interfaces) {
                fields.putAll(scanToMap(current, explode));
            }

            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    /**
     * Сканирует методы класса, получая методы, из него и подклассов
     *
     * @param clazz целевой класс
     * @param explode исключения в дереве
     * @return методы класса и супер класса
     */
    public static @NotNull Map<String, Method> scanMethodsToMap(Class<?> clazz, @NotNull Set<Class<?>> explode) {
        var fields = new HashMap<String, Method>();

        while(clazz != null) {
            if(explode.contains(clazz)) {
                break;
            }

            var interfaces = clazz.getInterfaces();

            for(var method: clazz.getDeclaredMethods()) {
                fields.put(method.getName(), method);
            }

            for(var current: interfaces) {
                fields.putAll(scanMethodsToMap(current, explode));
            }

            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    /**
     * Сканирует поля класса, получая поля, из него и подклассов
     *
     * @param clazz целевой класс
     * @param explode исключения в дереве
     * @return поля класса и супер класса
     */
    public static @NotNull Map<String, Field> scanFieldsToMap(Class<?> clazz, @NotNull Set<Class<?>> explode) {
        var fields = new HashMap<String, Field>();

        while(clazz != null) {
            if(explode.contains(clazz)) {
                break;
            }

            var interfaces = clazz.getInterfaces();

            for(var field: clazz.getDeclaredFields()) {
                fields.put(field.getName(), field);
            }

            for(var current: interfaces) {
                fields.putAll(scanFieldsToMap(current, explode));
            }

            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    /**
     * Получить новый безопасный экземпляр коллекции
     *
     * @param clazz класс коллекции
     * @return экземпляр коллекции
     */
    public static @NotNull Collection makeCollectionInstance(Class<?> clazz) {
        if(Set.class == clazz) {
            return new HashSet();
        } else if(List.class == clazz) {
            return new ArrayList();
        } else {
            try {
                return (Collection) clazz.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Получить новый безопасный экземпляр карты
     *
     * @param clazz класс карты
     * @return экземпляр карты
     */
    public static @NotNull Map makeMapInstance(Class<?> clazz) {
        if(Map.class == clazz) {
            return new HashMap<>();
        } else {
            try {
                return (Map) clazz.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static @NotNull Map<String, Method> scanMethodsToMap(Class<?> clazz) {
        return scanMethodsToMap(clazz, Set.of(Object.class));
    }

    public static @NotNull Map<String, Field> scanFieldsToMap(Class<?> clazz) {
        return scanFieldsToMap(clazz, Set.of(Object.class));
    }

    public static @NotNull Map<String, AccessibleObject> scanToMap(Class<?> clazz) {
        return scanToMap(clazz, Set.of(Object.class));
    }

    public static @NotNull List<Method> scanMethods(Class<?> clazz) {
        return scanMethods(clazz, Set.of(Object.class));
    }

    public static @NotNull List<Field> scanFields(Class<?> clazz) {
        return scanFields(clazz, Set.of(Object.class));
    }

    public static @NotNull List<AccessibleObject> scan(Class<?> clazz) {
        return scan(clazz, Set.of(Object.class));
    }
}
