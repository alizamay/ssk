package dev.niceone.smartsiteappserver.utils;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface Callback<T> {
    public void call(String key, T extra) throws JsonProcessingException;
}
