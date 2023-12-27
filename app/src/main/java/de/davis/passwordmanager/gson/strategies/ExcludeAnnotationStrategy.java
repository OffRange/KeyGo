package de.davis.passwordmanager.gson.strategies;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import de.davis.passwordmanager.gson.annotations.ExcludeFromGson;

public class ExcludeAnnotationStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return f.getAnnotation(ExcludeFromGson.class) != null;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return false;
    }
}
