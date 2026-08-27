package tritium.utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

public final class I18n {
    private static String loadedLanguage = "";
    private static Properties strings = new Properties();

    private I18n() {
    }

    public static String get(String key, Object... arguments) {
        String language = currentLanguage();
        if (!language.equals(loadedLanguage)) load(language);
        String value = strings.getProperty(key, key);
        return arguments.length == 0 ? value : String.format(value, arguments);
    }

    private static synchronized void load(String language) {
        if (language.equals(loadedLanguage)) return;
        Properties loaded = read("en_us");
        if (!"en_us".equals(language)) loaded.putAll(read(language));
        strings = loaded;
        loadedLanguage = language;
    }

    private static Properties read(String language) {
        Properties properties = new Properties();
        String path = "/tritium/lang/" + language + ".properties";
        try (InputStream stream = I18n.class.getResourceAsStream(path)) {
            if (stream != null) properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        return properties;
    }

    private static String currentLanguage() {
        return "zh_cn";
    }

    private static String normalize(String language) {
        String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.startsWith("zh") ? "zh_cn" : "en_us";
    }
}
