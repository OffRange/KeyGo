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

        String password = null;
        String username = null;
        String webDomain = null;
        String webDomainShort = null;

        for (FillContext context : contexts) {
            AssistStructure structure = context.getStructure();
            ParsedStructure parsedStructure = ParsedStructure.parse(structure, this);

            if(parsedStructure.isEmpty()){
                callback.onFailure("No Fields Found");
                return;
            }

            String pwd = extractText(parsedStructure.getPasswordView());
            String user = extractText(parsedStructure.getUsernameView());

            String domain = null;
            String domainShort = parsedStructure.getPasswordView().getIdPackage();
            if(parsedStructure.getWebDomainView() != null){
                domain = parsedStructure.getWebDomain();
                domainShort = parsedStructure.getWebDomainView().getWebDomain();
            }

            if(pwd != null)
                password = pwd;

            if(user != null)
                username = user;

            if(domain != null) {
                webDomain = domain;
                webDomainShort = domainShort;
            }

            if(webDomainShort == null)
                webDomainShort = parsedStructure.getPasswordView().getIdPackage();
        }

        if(!Response.VALIDATION_PATTERN.matcher(password +"").matches()) {
            callback.onFailure("Password does not matches");
            return;
        }

        String finalPassword = password;
        String finalUsername = username;
        String finalWebDomain = webDomain;
        String finalWebDomainShort = webDomainShort;

        if(finalPassword == null || finalWebDomainShort == null){
            callback.onFailure("Password or short web domain was null");
            return;
        }

        doInBackground(() -> SecureElementDatabase.createAndGet(this)
                .getSecureElementDao()
                .insert(new SecureElement(
                        new PasswordDetails(finalPassword, finalWebDomain, finalUsername), finalWebDomainShort)));
        callback.onSuccess();
    }

    private String extractText(AssistStructure.ViewNode viewNode){
        if(viewNode == null)
            return null;

        return viewNode.getText().toString();
    }
}
