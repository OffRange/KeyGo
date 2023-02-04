package de.davis.passwordmanager.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender;
import android.os.Build;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveInfo;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import androidx.annotation.RequiresApi;

import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.ui.login.LoginActivity;
import io.reactivex.rxjava3.schedulers.Schedulers;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Response {

    private final Context context;
    private final ParsedStructure parsedStructure;

    public Response(Context context, ParsedStructure parsedStructure) {
        this.context = context;
        this.parsedStructure = parsedStructure;
    }

    public FillResponse createAuthenticationResponse(){
        RemoteViews authView = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
        authView.setTextViewText(android.R.id.text1, context.getString(R.string.authentication_required));

        FillResponse.Builder builder = new FillResponse.Builder();

        if(count() > 0)
            builder.setAuthentication(parsedStructure.toIdArray(), getIntentSender(), authView);

        return attachSaveInfo(builder).build();
    }

    public FillResponse createRealResponse(){
        FillResponse.Builder responseBuilder = new FillResponse.Builder();

        fetchData().forEach(data -> {
            PasswordDetails details = (PasswordDetails) data.getDetail();

            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
            remoteViews.setTextViewText(android.R.id.text1, data.getTitle());

            Dataset.Builder datasetBuilder = new Dataset.Builder();
            for (AutofillId autofillId : parsedStructure.toIdArray()) {
                datasetBuilder.setValue(autofillId, AutofillValue.forText(autofillId == parsedStructure.getPasswordView().getAutofillId() ? details.getPassword() : details.getUsername()), remoteViews);
            }

            if(parsedStructure.toIdArray().length > 0)
                responseBuilder.addDataset(datasetBuilder.build());
        });

        attachSaveInfo(responseBuilder);
        return responseBuilder.build();
    }

    private FillResponse.Builder attachSaveInfo(FillResponse.Builder builder){
        SaveInfo.Builder saveInfoBuilder = new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD | SaveInfo.SAVE_DATA_TYPE_USERNAME, parsedStructure.toIdArray())
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE);

        AutofillId[] optionals = parsedStructure.getOptionals();
        if(optionals != null && optionals.length > 0)
            saveInfoBuilder.setOptionalIds(optionals);

        return builder.setSaveInfo(saveInfoBuilder.build());
    }

    private int count(){
        return SecureElementDatabase.createAndGet(context).getSecureElementDao().count()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .blockingGet();
    }

    private List<SecureElement> fetchData(){
        return SecureElementDatabase.createAndGet(context).getSecureElementDao()
                .getAllByType(SecureElement.TYPE_PASSWORD)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .blockingGet();
    }

    private IntentSender getIntentSender(){
        return PendingIntent.getActivity(
                context,
                1001,
                LoginActivity.getIntentForAuthentication(context),
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? PendingIntent.FLAG_MUTABLE : 0) | PendingIntent.FLAG_CANCEL_CURRENT
        ).getIntentSender();
    }
}
