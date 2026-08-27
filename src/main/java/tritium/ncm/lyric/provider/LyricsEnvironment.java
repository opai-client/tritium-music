package tritium.ncm.lyric.provider;

import java.io.File;

final class LyricsEnvironment {
    private static final File CONFIG_DIR = new File("TritiumMusic");

    static File configDir() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        return CONFIG_DIR;
    }

    static void log(String message) {
        System.out.println(message);
    }
}

