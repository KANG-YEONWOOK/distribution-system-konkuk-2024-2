package org.example.util;

import com.google.gson.*;

import java.time.LocalDateTime;

public class CustomGsonUtils {
    public static Gson CustomGson() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                    @Override
                    public JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
                        return new JsonPrimitive(src.toString()); // ISO-8601 형식
                    }
                })
                .registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                    @Override
                    public LocalDateTime deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context)
                            throws JsonParseException {
                        return LocalDateTime.parse(json.getAsString());
                    }
                })
                .create();
        return gson;
    }
}
