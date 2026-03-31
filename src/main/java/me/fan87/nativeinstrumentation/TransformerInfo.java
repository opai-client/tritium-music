package me.fan87.nativeinstrumentation;

import java.lang.instrument.ClassFileTransformer;

/**
 * @author IzumiiKonata
 * Date: 2025/7/12 12:56
 */
class TransformerInfo {
    final ClassFileTransformer mTransformer;
    String mPrefix;

    TransformerInfo(ClassFileTransformer transformer) {
        mTransformer = transformer;
        mPrefix = null;
    }

    ClassFileTransformer transformer() {
        return mTransformer;
    }

    String getPrefix() {
        return mPrefix;
    }

    void setPrefix(String prefix) {
        mPrefix = prefix;
    }
}
