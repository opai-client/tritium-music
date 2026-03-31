package tritium.reflection;

import me.fan87.nativeinstrumentation.NativeInstrumentation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author IzumiiKonata
 * Date: 2025/5/10 10:25
 */
public class ReflectionClasses {

    static final List<Class<?>> scanClasses = new ArrayList<>();

    public static void init() {
        synchronized (getScanClasses()) {
            scanClasses.clear();

            for (Class<?> clazz : NativeInstrumentation.getInstance().getAllLoadedClasses()) {
                if (clazz.getName().startsWith("MatrixShield")) {
                    scanClasses.add(clazz);
//                    System.out.println(clazz.getName());
                }
            }
        }
    }

    public static synchronized List<Class<?>> getScanClasses() {
        return scanClasses;
    }

    static {
        init();
    }

}
