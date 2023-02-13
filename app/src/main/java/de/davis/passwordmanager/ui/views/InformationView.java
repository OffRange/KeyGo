package de.davis.passwordmanager.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dialog.EditDialog;
import de.davis.passwordmanager.manager.CopyManager;
import de.davis.passwordmanager.ui.views.copy.CopyView;

public class InformationView extends MaterialCardView implements CopyView {

    private static final char DOT = '\u2022';
    private static final String TAG = "edit_dialog";

    private TextView titleView;
    private TextView informationView;
    private ImageView iconView;
    private TextView requiredView;

    @LayoutRes
    private int layoutDialog;

    private CheckableImageButton endButton;

    private String title;
    private String information;

    private int maxLength;
    private int inputType;

    private boolean applyEmpties;
    private boolean printRequired;
    private boolean secret;
    private boolean changeable;
    private boolean copyable;

    private boolean hasOnLongClickListeners;

    private boolean isMoving;

    private boolean featureDisabled;

    private boolean initiated;

    private EditDialog.OnInformationChangeListener onInformationChangeListener;

    private final EditDialog.Configuration configuration = new EditDialog.Configuration();

    public InformationView(Context context) {
        this(context, null, com.google.android.material.R.attr.materialCardViewElevatedStyle);
    }

    public InformationView(Context context, AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewElevatedStyle);
    }

    public InformationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.InformationView, defStyle, 0);

        information = a.getString(R.styleable.InformationView_information);
        layoutDialog = a.getResourceId(R.styleable.InformationView_dialogLayout, 0);

        setFocusable(false);

        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_information_view, this, true);

        endButton = view.findViewById(R.id.imageButton);
        setOnEndButtonClickListener(v -> {
            secret = !secret;
            transformString();
        });

        titleView = view.findViewById(android.R.id.title);
        requiredView = view.findViewById(R.id.required);


        boolean multiline = a.getBoolean(R.styleable.InformationView_multiline, false);
        if(a.getBoolean(R.styleable.InformationView_contentEnabled, true)){
            int layoutContent = a.getResourceId(R.styleable.InformationView_contentLayout, multiline ? R.layout.information_view_content_multi_line : R.layout.information_view_content);
            View content = LayoutInflater.from(getContext()).inflate(layoutContent, view.findViewById(R.id.container), true);
            if (layoutContent != R.layout.information_view_content && layoutContent != R.layout.information_view_content_multi_line) {
                disableFeatures();
            }else
                defaultContentLayout(content);
        }else
            disableFeatures();

        maxLength = a.getInteger(R.styleable.InformationView_android_maxLength, -1);
        inputType = a.getInteger(R.styleable.InformationView_android_inputType, InputType.TYPE_CLASS_TEXT);

        iconView = view.findViewById(R.id.imageView);

        setApplyEmpties(a.getBoolean(R.styleable.InformationView_applyEmpties, true));
        printRequired(a.getBoolean(R.styleable.InformationView_print_required, false));
        setSecret(a.getBoolean(R.styleable.InformationView_secret, false));
        setChangeable(a.getBoolean(R.styleable.InformationView_changeable, false));
        setCopyable(a.getBoolean(R.styleable.InformationView_copyable, false));

        setTitle(a.getString(R.styleable.InformationView_title));

        if (a.hasValue(R.styleable.InformationView_android_icon)) {
            setIconDrawable(a.getDrawable(R.styleable.InformationView_android_icon));
        }

        if (a.hasValue(R.styleable.InformationView_endButtonDrawable)) {
            setEndButtonDrawable(a.getDrawable(R.styleable.InformationView_endButtonDrawable));
        }

        if (a.hasValue(R.styleable.InformationView_informationTextColor)) {
            setInformationTextColor(a.getColor(R.styleable.InformationView_informationTextColor, MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, Color.BLACK)));
        }

        a.recycle();

        initiated = true;
    }

    private void disableFeatures(){
        featureDisabled = true;
    }

    private void defaultContentLayout(View view){
        informationView = view.findViewById(android.R.id.text1);

        View horizontalScrollView = view.findViewById(R.id.horizontalScrollView);

        if(horizontalScrollView == null)
            return;

        //This is required because the scroll view will intercept any touch events made on the parent
        //above that view. This listener is required to recognize the touch events anyway.
        preventInterceptingTouchEvents(horizontalScrollView);
    }

    public void preventInterceptingTouchEvents(View view){
        view.setOnTouchListener((v, event) -> {
            v.performClick();
            int action = event.getAction();
            if(action == MotionEvent.ACTION_MOVE) {
                MotionEvent ev = MotionEvent.obtain(event);
                ev.setAction(MotionEvent.ACTION_CANCEL);
                onTouchEvent(ev);
                ev.recycle();
                isMoving = true;
                return false;
            }

            onTouchEvent(event);
            isMoving = false;
            return true;
        });
    }

    public View getContent() {
        return findViewById(R.id.container);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(isMoving)
            return true;

        return super.onTouchEvent(event);
    }

    public EditDialog.Configuration getConfiguration() {
        return configuration;
    }

    public void setOnChangedListener(EditDialog.OnInformationChangeListener onInformationChangeListener) {
        this.onInformationChangeListener = onInformationChangeListener;
    }

    public void setOnEditDialogViewCreatedListener(EditDialog.OnViewCreatedListener onEditDialogViewCreatedListener) {
        this.configuration.setOnEditDialogViewCreatedListener(onEditDialogViewCreatedListener);
    }


    public void setIconDrawable(Drawable drawable){
        iconView.setImageDrawable(drawable);
        iconView.setVisibility(drawable == null ? GONE : VISIBLE);
    }

    public Drawable getIconDrawable(){
        return iconView.getDrawable();
    }

    private String getDotString(){
        if(information == null)
            return null;

        StringBuilder s = new StringBuilder();
        for (int i = 0; i < information.length(); i++) {
            s.append(DOT);
        }

        return s.toString();
    }

    public void setTitle(String title){
        this.title = title;
        String modifiedTitle = title +(printRequired ? "*" : "");
        titleView.setText(modifiedTitle);
    }

    public void setInformation(String information){
        this.information = information;
        handleEndIconVisibility();
        transformString();
    }

    public void setInformation(@StringRes int stringRes){
        setInformation(getContext().getString(stringRes));
    }

    public String getInformation() {
        return information;
    }

    public void setInformationTextColor(@ColorInt int color){
        informationView.setTextColor(color);
    }

    private void handleEndIconVisibility(){
        endButton.setVisibility(secret && isInformationSet() ? VISIBLE : GONE);
    }

    public void setSecret(boolean secret) {
        handleEndIconVisibility();
        this.secret = secret;
        transformString();
    }

    public boolean isSecret() {
        return secret;
    }

    public void setChangeable(boolean changeable){
        this.changeable = changeable;
        handleLongClickable();
    }

    public boolean isChangeable() {
        return changeable;
    }

    public void setApplyEmpties(boolean applyEmpties) {
        // Prevent the view from applying empty changes
        this.applyEmpties = applyEmpties;
    }

    public void printRequired(boolean print){
        this.printRequired = print;
        requiredView.setVisibility(print ? VISIBLE : GONE);
        setTitle(title);
    }

    public void setCopyable(boolean copyable){
        this.copyable = copyable;
        setOnClickListener(copyable ? new CopyManager.Listener() : null);
    }

    public boolean isCopyable() {
        return copyable;
    }

    private void transformString(){
        if(featureDisabled)
            return;

        if(secret && isInformationSet()) informationView.setText(getDotString());
        else informationView.setText(!isInformationSet() ? getContext().getString(R.string.no_value_set) : information);
    }

    private boolean isInformationSet(){
        return !(information == null || information.trim().isEmpty());
    }

    public void setEndButtonDrawable(Drawable drawable){
        endButton.setVisibility(drawable == null ? GONE : VISIBLE);
        endButton.setImageDrawable(drawable);
    }

    public void setOnEndButtonClickListener(OnClickListener listener){
        endButton.setOnClickListener(listener);
    }

    public Details getDetails(){
        return new Details(titleView.getText().toString(), information, maxLength, isSecret(), iconView.getDrawable(), inputType);
    }

    @Override
    public boolean performClick() {
        if(!isInformationSet())
            showDialog();

        return super.performClick();
    }

    @Override
    public boolean performLongClick() {
        if(isInformationSet())
            showDialog();

        return super.performLongClick();
    }

    @Override
    public void setOnLongClickListener(@Nullable OnLongClickListener l) {
        super.setOnLongClickListener(l);
        hasOnLongClickListeners = l != null;
        handleLongClickable();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if(!initiated){
            super.addView(child, index, params);
            return;
        }

        ((ViewGroup)findViewById(R.id.container)).addView(child, index, params);
    }

    private void handleLongClickable(){
        setLongClickable(changeable || hasOnLongClickListeners);
    }

    private void showDialog(){
        if(!changeable)
            return;

        if(!(getContext() instanceof AppCompatActivity))
            return;

        configuration.setDetails(getDetails());
        configuration.setAdditionalView(layoutDialog);
        configuration.setOnInformationChangeListener(new EditChangeListener());

        EditDialog editDialog = new EditDialog(configuration);
        editDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), TAG);
    }

    @Override
    public String getCopyString() {
        return getInformation();
    }

    private class EditChangeListener implements EditDialog.OnInformationChangeListener {

        @Override
        public void onInformationChanged(EditDialog dialog, String information) {
            if(!applyEmpties && information != null && information.trim().isEmpty())
                return;

            setInformation(information);
            if(onInformationChangeListener != null)
                onInformationChangeListener.onInformationChanged(dialog, information);
        }
    }

    public static class Details implements Parcelable {

        private final String title;
        private String information;

        private final int maxLength;
        private final int inputType;
        private final boolean secret;
        private final Drawable drawable;

        public Details(String title, String information, int maxLength, boolean secret, Drawable drawable, int inputType) {
            this.title = title;
            this.information = information;
            this.maxLength = maxLength;
            this.secret = secret;
            this.drawable = drawable;
            this.inputType = inputType;
        }

        public Details(Parcel in){
            title = in.readString();
            information = in.readString();
            maxLength = in.readInt();
            inputType = in.readInt();
            secret = in.readInt() == 1;

            if(in.dataSize()-1 == in.dataPosition())
                drawable = new BitmapDrawable(Resources.getSystem(), (Bitmap) in.readParcelable(getClass().getClassLoader()));
            else drawable = null;
        }

        public String getTitle() {
            return title;
        }

        public String getInformation() {
            return information;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public int getInputType() {
            return inputType;
        }

        public boolean isSecret() {
            return secret;
        }

        public Drawable getDrawable() {
            return drawable;
        }

        public void setInformation(String information) {
            this.information = information;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(title);
            dest.writeString(information);
            dest.writeInt(maxLength);
            dest.writeInt(inputType);
            dest.writeInt(secret ? 1 : 0);
            if(drawable instanceof BitmapDrawable)
                dest.writeParcelable(((BitmapDrawable) drawable).getBitmap(), flags);
        }

        public static final Parcelable.Creator<Details> CREATOR
                = new Parcelable.Creator<>() {
            public Details createFromParcel(Parcel in) {
                return new Details(in);
            }

            public Details[] newArray(int size) {
                return new Details[size];
            }
        };
    }
}