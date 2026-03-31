package tritium.reflection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import tritium.utils.Tuple;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author IzumiiKonata
 * Date: 2025/5/10 10:22
 */
@UtilityClass
public class ClassFinder {

    public static final int DONT_CARE = 0x2304;

    public Finder finder() {
        return new Finder();
    }

    public static class Finder {

        public enum StrictMode {
            Fields,
            Methods,
            All,
            None
        }

        final List<MethodData> methods = new ArrayList<>();
        final List<FieldData> fields = new ArrayList<>();
        Class<?> extendsClass = Object.class;
        List<Class<?>> implementsClasses = new ArrayList<>() {
            {
                this.add(Object.class);
            }
        };
        StrictMode strictMode = StrictMode.None;

        boolean debugMode = false;
        boolean isInterface = false;

        public Finder setInterface() {
            this.isInterface = true;
            return this;
        }

        public Finder debugMode() {
            debugMode = true;
            return this;
        }

        public Finder setStrictMode(StrictMode mode) {
            this.strictMode = mode;
            return this;
        }

        public Tuple<Boolean, Boolean> compareTypes(Class<?> toBeChecked, Class<?> other) {
            boolean notEquals = toBeChecked != other;

            // 如果 isAssignableFrom 则跳过检查.
            // Object.class
            boolean assignableFrom = other.isAssignableFrom(toBeChecked);

            return new Tuple<>(notEquals, assignableFrom);
        }

        public Finder addField(Class<?> type, int modifiers) {
            this.fields.add(new FieldData(type, modifiers));
            return this;
        }

        public Finder addMethod(Class<?> returnType, int modifier, Class<?>... parameterTypes) {
            this.methods.add(new MethodData(returnType, modifier, parameterTypes));
            return this;
        }

        public Finder extendsClass(Class<?> type) {
            this.extendsClass = type;
            return this;
        }

        public Finder implementsClass(Class<?> type) {
            this.implementsClasses.add(type);
            return this;
        }

        boolean disableRetry = false;

        public Finder disableRetry() {
            this.disableRetry = true;
            return this;
        }

        public Class<?> find() {
            return this.find(5);
        }

        public Class<?> find(int retryTimes) {

            if (this.disableRetry)
                return this.innerFind();

            int count = 0;
            Throwable lastThrowable = new Error("Unknown");
            while (count < retryTimes) {
                try {
                    return this.innerFind();
                } catch (Throwable t) {
                    ReflectionClasses.init();
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                    count += 1;
                    lastThrowable = t;
                }
            }

            throw new RuntimeException(lastThrowable);
        }

        private Class<?> innerFind() {

            List<Class<?>> result = new ArrayList<>();

            synchronized (ReflectionClasses.getScanClasses()) {
                classes:
                for (Class<?> aClass : ReflectionClasses.getScanClasses()) {

                    if (debugMode)
                        System.out.println("Class: " + aClass.getName());

                    if (!extendsClass.isAssignableFrom(aClass)) {

                        if (debugMode)
                            System.out.println("Not extending class: " + extendsClass.getName());

                        continue classes;
                    }

                    for (Class<?> clazz : implementsClasses) {
                        if (!clazz.isAssignableFrom(aClass)) {
                            if (debugMode)
                                System.out.println("Not implementing class: " + extendsClass.getName());

                            continue classes;
                        }
                    }

                    if (this.isInterface && !aClass.isInterface()) {
                        if (debugMode)
                            System.out.println("Not interface");
                        continue classes;
                    }

                    Field[] declaredFields = aClass.getDeclaredFields();
                    Method[] declaredMethods = aClass.getDeclaredMethods();

                    if (strictMode == StrictMode.None) {
                        if (declaredFields.length < fields.size() || declaredMethods.length < methods.size()) {

                            if (debugMode)
                                System.out.println("Continuing because field size or method size mismatch");

                            continue;
                        }
                    } else {

                        if ((strictMode == StrictMode.Fields || strictMode == StrictMode.All) && declaredFields.length != fields.size()) {

                            if (debugMode)
                                System.out.println("Continuing because field size mismatch with strict mode");

                            continue;
                        }

                        if ((strictMode == StrictMode.Methods || strictMode == StrictMode.All) && declaredMethods.length != methods.size()) {

                            if (debugMode)
                                System.out.println("Continuing because method size mismatch with strict mode");

                            continue;
                        }
                    }


                    // 遍历所有 field
                    final List<Field> fieldList = new ArrayList<>();
                    Collections.addAll(fieldList, declaredFields);

                    fields:
                    for (FieldData fieldData : this.fields) {

                        boolean found = false;

                        Iterator<Field> it = fieldList.iterator();

                        fieldLoop:
                        while (it.hasNext()) {
                            Field nextField = it.next();

                            nextField.setAccessible(true);

                            Tuple<Boolean, Boolean> tuple = this.compareTypes(nextField.getType(), fieldData.fieldType);
                            boolean notEquals = tuple.getA();
                            boolean assignableFrom = tuple.getB();

                            if (notEquals && !assignableFrom) {

                                if (debugMode)
                                    System.out.println("Field: " + fieldData.fieldType + " != " + nextField.getType());

                                continue fieldLoop;
                            }

                            if (nextField.getModifiers() != fieldData.modifiers && fieldData.modifiers != DONT_CARE) {

                                if (debugMode)
                                    System.out.println("Field: " + nextField.getName() + "(" + nextField.getType().getName() + ")'s modifier (" + nextField.getModifiers() + ") != " + fieldData.modifiers);

                                continue fieldLoop;
                            }

                            it.remove();
                            if (debugMode)
                                System.out.println("Field Found: " + fieldData.fieldType);
                            found = true;
                            break;
                        }

                        if (!found) {
                            if (debugMode)
                                System.out.println("Field Not Found: " + fieldData.fieldType);
                        }

                    }

                    if (declaredFields.length - fieldList.size() != fields.size()) {
                        if (debugMode)
                            System.out.println("Continuing because field size mismatch");
                        continue;
                    }

                    // 遍历所有 Method
                    final List<Method> methodList = new ArrayList<>();
                    Collections.addAll(methodList, declaredMethods);

                    methods:
                    for (MethodData methodData : this.methods) {

                        boolean found = false;

                        Iterator<Method> it = methodList.iterator();

                        methodLoop:
                        while (it.hasNext()) {
                            Method nextMethod = it.next();

                            nextMethod.setAccessible(true);

                            // compare return type
                            {
                                Tuple<Boolean, Boolean> tuple = this.compareTypes(nextMethod.getReturnType(), methodData.returnType);
                                boolean notEquals = tuple.getA();
                                boolean assignableFrom = tuple.getB();

                                if (notEquals && !assignableFrom) {
                                    continue methodLoop;
                                }

                            }

                            Class<?>[] pTypes = nextMethod.getParameterTypes();

                            if (pTypes.length != methodData.parameterTypes.length) {
                                continue methodLoop;
                            }

                            for (int i = 0; i < pTypes.length; i++) {
                                Class<?> argType = pTypes[i];
                                Class<?> compare = methodData.parameterTypes[i];

                                Tuple<Boolean, Boolean> tuple = this.compareTypes(argType, compare);
                                boolean notEquals = tuple.getA();
                                boolean assignableFrom = tuple.getB();

                                if (notEquals && !assignableFrom) {
                                    continue methodLoop;
                                }
                            }

                            if (nextMethod.getModifiers() != methodData.modifiers && methodData.modifiers != DONT_CARE) {
                                continue methodLoop;
                            }

                            it.remove();
                            found = true;
                            break;
                        }

                        if (!found) {
                            if (debugMode)
                                System.out.println("Method not found: " + methodData.returnType + "(" + this.methods.indexOf(methodData) + ")");
                        }

                    }

                    if (declaredMethods.length - methodList.size() != methods.size()) {
                        if (debugMode)
                            System.out.println("Continuing because method size mismatch");
                        continue;
                    }

                    result.add(aClass);

                }
            }

            if (result.size() == 1) {
                return result.get(0);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[ ");

            result.forEach(r -> sb.append(r.getName()).append(", "));

            throw new IllegalStateException(sb + "] Found " + result.size() + " classes.");

        }

    }

    @Getter
    @AllArgsConstructor
    public static class FieldData {

        private final Class<?> fieldType;
        private final int modifiers;

    }

    @Getter
    @AllArgsConstructor
    public static class MethodData {

        private final Class<?> returnType;
        private final int modifiers;
        private final Class<?>[] parameterTypes;

    }

}
