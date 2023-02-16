package de.davis.passwordmanager.service;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.app.assist.AssistStructure;
import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.List;

import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.password.PasswordDetails;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AutoFillService extends AutofillService {

    @Override
    public void onFillRequest(@NonNull FillRequest request, @NonNull CancellationSignal cancellationSignal, @NonNull FillCallback callback) {
        List<FillContext> contexts = request.getFillContexts();
        AssistStructure structure = contexts.get(contexts.size() -1).getStructure();
        if(structure.getActivityComponent().getPackageName().equals(getPackageName())) {
            callback.onFailure("own package");
            return;
        }

        ParsedStructure parsedStructure = ParsedStructure.parse(structure, this);

        if(parsedStructure.isEmpty()){
            callback.onFailure("No Fields Found");
            return;
        }

        FillResponse fillResponse = new Response(this, parsedStructure, request).createAuthenticationResponse();

        callback.onSuccess(fillResponse);
    }

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
        List<FillContext> contexts = request.getFillContexts();
        AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
        ParsedStructure parsedStructure = ParsedStructure.parse(structure, this);

        if(parsedStructure.isEmpty()){
            callback.onFailure("No Fields Found");
            return;
        }

        String password = parsedStructure.getPasswordView().getText().toString();

        String username = null;
        if(parsedStructure.getUsernameView() != null)
            username = parsedStructure.getUsernameView().getText().toString();

        String webDomain = null;
        String webDomainShort = parsedStructure.getPasswordView().getIdPackage();

        if(parsedStructure.getWebDomainView() != null){
            webDomain = parsedStructure.getWebDomain();
            webDomainShort = parsedStructure.getWebDomainView().getWebDomain();
        }

        String finalUsername = username;
        String finalWebDomain = webDomain;
        String finalWebDomainShort = webDomainShort;
        doInBackground(() -> SecureElementDatabase.createAndGet(this)
                .getSecureElementDao()
                .insert(new SecureElement(
                        new PasswordDetails(password, finalWebDomain, finalUsername), finalWebDomainShort)));
        callback.onSuccess();
    }
}
