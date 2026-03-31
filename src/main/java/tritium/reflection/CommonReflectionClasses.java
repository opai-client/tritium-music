package tritium.reflection;

import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.Logger;
import tritium.utils.Lazy;

import static java.lang.reflect.Modifier.*;

/**
 * @author IzumiiKonata
 * Date: 2025/8/2 10:03
 */
@UtilityClass
public class CommonReflectionClasses {

    public final Lazy<Class<?>> ResourceLocation = Lazy.of(
            () -> ClassFinder.finder()
                    .addField(String.class, PROTECTED | FINAL)
                    .addField(String.class, PROTECTED | FINAL)
                    .addMethod(String[].class, PROTECTED | STATIC, String.class)
                    .find()
    );

    public final Lazy<Class<?>> Minecraft = Lazy.of(
            () -> ClassFinder.finder()

                    .addField(Logger.class, PRIVATE | STATIC | FINAL)
                    .addField(Thread.class, PRIVATE | FINAL)
                    .addField(boolean.class, PUBLIC | VOLATILE)
                    .addField(int.class, PRIVATE | STATIC)

                    .find()
    );

}
