package de.davis.passwordmanager.sync.keygo;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.textfield.TextInputLayout;
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
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.dialog.EditDialogBuilder;
import de.davis.passwordmanager.gson.strategies.ExcludeAnnotationStrategy;
import de.davis.passwordmanager.security.Cryptography;
import de.davis.passwordmanager.security.element.ElementDetail;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementDetail;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.sync.DataTransfer;
import de.davis.passwordmanager.sync.Result;
import de.davis.passwordmanager.ui.views.InformationView;

public class KeyGoTransfer extends DataTransfer {

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

    public KeyGoTransfer(Context context) {
        super(context);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(ElementDetail.class, new ElementDetailTypeAdapter())
                .setExclusionStrategies(new ExcludeAnnotationStrategy())
                .create();
    }

    @Override
    protected Result importElements(InputStream inputStream, String password) throws Exception{
        byte[] file = IOUtils.toByteArray(inputStream);
        if(file.length == 0)
            return new Result.Error(getContext().getString(R.string.invalid_file_length));

        file = Cryptography.decryptWithPwd(file, password);
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
        for (SecureElement element : list) {
            if(elements.stream().anyMatch(e -> e.getTitle().equals(element.getTitle())
                    && e.getDetail().equals(element.getDetail()))) {
                existed++;
                continue;
            }

            SecureElementManager.getInstance().createElement(element);
        }

        if(existed != 0)
            return new Result.Error(getContext().getResources().getQuantityString(R.plurals.item_title_existed, existed, existed));

        return new Result.Success(TYPE_IMPORT);
    }

    @Override
    protected Result exportElements(OutputStream outputStream, String password) throws Exception {
        List<SecureElement> elements = SecureElementDatabase.getInstance()
                .getSecureElementDao()
                .getAllOnce()
                .blockingGet();

        String j = gson.toJson(elements);

        outputStream.write(Cryptography.encryptWithPwd(j.getBytes(), password));
        outputStream.close();

        return new Result.Success(TYPE_EXPORT);
    }

    @Override
    public void start(int type, Uri uri) {
        InformationView.Information i = new InformationView.Information();
        i.setHint(getContext().getString(R.string.password));
        i.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        i.setSecret(true);

        AlertDialog alertDialog = new EditDialogBuilder(getContext())
                .setTitle(R.string.password)
                .setPositiveButton(R.string.yes, (dialog, which) -> {})
                .withInformation(i)
                .withStartIcon(AppCompatResources.getDrawable(getContext(), R.drawable.ic_baseline_password_24))
                .setCancelable(false)
                .show();

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            alertDialog.dismiss();
            String password = ((EditText)alertDialog.findViewById(R.id.textInputEditText)).getText().toString();

            if(password.isEmpty()){
                ((TextInputLayout)alertDialog.findViewById(R.id.textInputLayout))
                        .setError(getContext().getString(R.string.is_not_filled_in));
                return;
            }

            start(type, uri, password);
        });
    }
}
