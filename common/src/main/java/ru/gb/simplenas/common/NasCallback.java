package ru.gb.simplenas.common;

@FunctionalInterface
public interface NasCallback {
    void callback (Object... objects);
}
