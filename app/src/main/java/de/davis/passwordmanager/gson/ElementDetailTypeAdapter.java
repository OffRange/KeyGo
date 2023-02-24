package de.davis.passwordmanager.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

import de.davis.passwordmanager.security.element.ElementDetail;
import de.davis.passwordmanager.security.element.SecureElementDetail;

public class ElementDetailTypeAdapter implements JsonSerializer<ElementDetail>, JsonDeserializer<ElementDetail> {

    @Override
    public ElementDetail deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        int type = json.getAsJsonObject().get("type").getAsInt();
        return context.deserialize(json, SecureElementDetail.getFor(type).getElementDetailClass());
    }

    @Override
    public JsonElement serialize(ElementDetail src, Type typeOfSrc, JsonSerializationContext context) {
        JsonElement jsonObject = context.serialize(src);
        jsonObject.getAsJsonObject().addProperty("type", src.getType());
        return jsonObject;
    }
}
