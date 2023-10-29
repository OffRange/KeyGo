package de.davis.passwordmanager.backup.keygo;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.backup.Result;
import de.davis.passwordmanager.backup.SecureDataBackup;
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.gson.strategies.ExcludeAnnotationStrategy;
import de.davis.passwordmanager.security.Cryptography;
import de.davis.passwordmanager.security.element.ElementDetail;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementDetail;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.security.element.password.PasswordDetails;

public class KeyGoBackup extends SecureDataBackup {

    public static class ElementDetailTypeAdapter implements JsonSerializer<ElementDetail>, JsonDeserializer<ElementDetail> {

        @Override
        public ElementDetail deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            int type = json.getAsJsonObject().get("type").getAsInt();
            if(type == SecureElement.TYPE_PASSWORD) {
                JsonArray passwordArray = new JsonArray();
                for (byte b : Cryptography.encryptAES(json.getAsJsonObject().get("password").getAsString().getBytes())) {
                    passwordArray.add(b);
                }
                json.getAsJsonObject().add("password", passwordArray);
            }
            return context.deserialize(json, SecureElementDetail.getFor(type).getElementDetailClass());
        }

        @Override
        public JsonElement serialize(ElementDetail src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            JsonElement jsonObject = context.serialize(src);
            if(src instanceof PasswordDetails pwdSrc)
                jsonObject.getAsJsonObject().addProperty("password", pwdSrc.getPassword());

            jsonObject.getAsJsonObject().addProperty("type", src.getType());
            return jsonObject;
        }
    }

    private final Gson gson;

    public KeyGoBackup(Context context) {
        super(context);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(ElementDetail.class, new ElementDetailTypeAdapter())
                .setExclusionStrategies(new ExcludeAnnotationStrategy())
                .create();
    }

    @NonNull
    @Override
    protected Result runImport(InputStream inputStream) throws Exception{
        byte[] file = IOUtils.toByteArray(inputStream);
        if(file.length == 0)
            return new Result.Error(getContext().getString(R.string.invalid_file_length));

        file = Cryptography.decryptWithPwd(file, getPassword());
        List<SecureElement> list;
        try{
            list = gson.fromJson(new String(file), new TypeToken<ArrayList<SecureElement>>(){}.getType());
        }catch (Exception e){
            return new Result.Error(getContext().getString(R.string.invalid_file));
        }

        List<SecureElement> elements = SecureElementDatabase.getInstance()
                .getSecureElementDao()
                .getAllOnce()
                .blockingGet();

        int existed = 0;
        int length = list.size();
        for (int i = 0; i < length; i++) {
            SecureElement element = list.get(i);
            if(elements.stream().anyMatch(e -> e.getTitle().equals(element.getTitle())
                    && e.getDetail().equals(element.getDetail()))) {
                existed++;

                notifyUpdate(i+1, length);
                continue;
            }

            SecureElementManager.getInstance().createElement(element);
            notifyUpdate(i+1, length);
        }

        if(existed != 0)
            return new Result.Duplicate(existed);

        return new Result.Success(TYPE_IMPORT);
    }

    @NonNull
    @Override
    protected Result runExport(OutputStream outputStream) throws Exception {
        List<SecureElement> elements = SecureElementDatabase.getInstance()
                .getSecureElementDao()
                .getAllOnce()
                .blockingGet();

        String j = gson.toJson(elements);

        outputStream.write(Cryptography.encryptWithPwd(j.getBytes(), getPassword()));
        outputStream.close();

        return new Result.Success(TYPE_EXPORT);
    }
}
