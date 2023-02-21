package de.davis.passwordmanager.service;

import android.app.PendingIntent;
import android.app.slice.Slice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.BlendMode;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.Field;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.InlinePresentation;
import android.service.autofill.Presentations;
import android.service.autofill.RegexValidator;
import android.service.autofill.SaveInfo;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.DrawableRes;
import androidx.annotation.RequiresApi;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import java.util.List;
import java.util.regex.Pattern;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.ui.login.LoginActivity;
import io.reactivex.rxjava3.schedulers.Schedulers;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Response {

    public static final String EXTRA_FILL_REQUEST = "fill_request";

    private final Context context;
    private final ParsedStructure parsedStructure;
    private final FillRequest request;

    public Response(Context context, ParsedStructure parsedStructure, FillRequest request) {
        this.context = context;
        this.parsedStructure = parsedStructure;
        this.request = request;
    }

    public FillResponse createAuthenticationResponse(){
        String authRequired = context.getString(R.string.autofill_service);
        RemoteViews authView = createRemoteView(authRequired);

        FillResponse.Builder builder = new FillResponse.Builder();

        if(count() > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InlinePresentation inlinePresentation = createInlinePresentation(request.getInlineSuggestionsRequest().getInlinePresentationSpecs().get(0), authRequired, R.mipmap.ic_launcher_round);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    builder.setAuthentication(parsedStructure.toIdArray(), getIntentSender(), new Presentations.Builder()
                            .setInlinePresentation(inlinePresentation)
                            .setMenuPresentation(authView).build());
                }else
                    builder.setAuthentication(parsedStructure.toIdArray(), getIntentSender(), authView, inlinePresentation);
            }
            else
                builder.setAuthentication(parsedStructure.toIdArray(), getIntentSender(), authView);
        }

        return attachSaveInfo(builder).build();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private InlinePresentation createInlinePresentation(InlinePresentationSpec inlinePresentationSpec, String text, @DrawableRes int iconId){
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        Slice slice = createInlinePresentationSlice(inlinePresentationSpec, text, pendingIntent, iconId);

        return new InlinePresentation(slice, inlinePresentationSpec, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private Slice createInlinePresentationSlice(InlinePresentationSpec inlinePresentationSpec, String text, PendingIntent pendingIntent, int iconId) {
        Bundle imeStyle = inlinePresentationSpec.getStyle();
        if (!UiVersions.getVersions(imeStyle).contains(UiVersions.INLINE_UI_VERSION_1))
            return null;

        InlineSuggestionUi.Content.Builder contentBuilder = InlineSuggestionUi.newContentBuilder(pendingIntent).setContentDescription("Autofill option");
        if (!text.isBlank())
            contentBuilder.setTitle(text);

        if(iconId > 0){
            Icon icon = Icon.createWithResource(context, iconId);
            if(icon != null){
                icon.setTintBlendMode(BlendMode.DST);
                contentBuilder.setStartIcon(icon);
            }
        }


        return contentBuilder.build().getSlice();
    }

    private RemoteViews createRemoteView(String title){
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
        remoteViews.setTextViewText(android.R.id.text1, title);
        return remoteViews;
    }

    public FillResponse createRealResponse(){
        FillResponse.Builder responseBuilder = new FillResponse.Builder();

        attachDatasets(responseBuilder);
        attachSaveInfo(responseBuilder);
        return responseBuilder.build();
    }

    private void attachDatasets(FillResponse.Builder responseBuilder){
        List<SecureElement> elements = fetchData();
        int maxItems = getMaxItems(elements);

        for (int i = 0; i < maxItems; i++) {
            SecureElement element = elements.get(i);
            PasswordDetails details = (PasswordDetails) element.getDetail();

            RemoteViews remoteViews = createRemoteView(element.getTitle());

            Dataset.Builder datasetBuilder = new Dataset.Builder();
            for (AutofillId autofillId : parsedStructure.toIdArray()) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    datasetBuilder.setValue(autofillId, AutofillValue.forText(autofillId == parsedStructure.getPasswordView().getAutofillId() ? details.getPassword() : details.getUsername()), remoteViews);
                    continue;
                }

                InlinePresentation inlinePresentation = createInlinePresentation(request.getInlineSuggestionsRequest().getInlinePresentationSpecs().get(i), element.getTitle(), -1);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    datasetBuilder.setField(autofillId, new Field.Builder().setValue(AutofillValue.forText(autofillId == parsedStructure.getPasswordView().getAutofillId() ? details.getPassword() : details.getUsername()))
                            .setPresentations(new Presentations.Builder()
                                    .setMenuPresentation(remoteViews)
                                    .setInlinePresentation(inlinePresentation)
                                    .build())
                            .build());
                    continue;
                }

                datasetBuilder.setValue(autofillId, AutofillValue.forText(autofillId == parsedStructure.getPasswordView().getAutofillId() ? details.getPassword() : details.getUsername()), remoteViews, inlinePresentation);
            }


            if(parsedStructure.toIdArray().length > 0)
                responseBuilder.addDataset(datasetBuilder.build());
        }
    }

    private int getMaxItems(List<SecureElement> elements){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            int maxSuggestionCount = request.getInlineSuggestionsRequest().getMaxSuggestionCount();
            if(maxSuggestionCount > 0)
                return Math.min(maxSuggestionCount, elements.size());
        }

        return elements.size();
    }

    private FillResponse.Builder attachSaveInfo(FillResponse.Builder builder){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if((request.getFlags() & FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST) != 0)
                return builder;
        }

        SaveInfo.Builder saveInfoBuilder = new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD | SaveInfo.SAVE_DATA_TYPE_USERNAME, parsedStructure.toIdArray())
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE);

        AutofillId[] optionals = parsedStructure.getOptionals();
        if(optionals != null && optionals.length > 0)
            saveInfoBuilder.setOptionalIds(optionals);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            saveInfoBuilder.setTriggerId(parsedStructure.getPasswordView().getAutofillId());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            saveInfoBuilder.setValidator(new RegexValidator(parsedStructure.getPasswordView().getAutofillId(), Pattern.compile("^[A-Za-z\\d].*")));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveInfoBuilder.setFlags(SaveInfo.FLAG_DELAY_SAVE);
        }
        
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
                LoginActivity.getIntentForAuthentication(context).putExtra(EXTRA_FILL_REQUEST, request),
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? PendingIntent.FLAG_MUTABLE : 0) | PendingIntent.FLAG_CANCEL_CURRENT
        ).getIntentSender();
    }
}
