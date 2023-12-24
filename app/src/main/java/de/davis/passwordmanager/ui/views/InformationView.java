package de.davis.passwordmanager.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.text.method.TransformationMethod;
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
import de.davis.passwordmanager.dialog.BaseDialogBuilder;
import de.davis.passwordmanager.dialog.EditDialogBuilder;
import de.davis.passwordmanager.manager.CopyManager;
import de.davis.passwordmanager.ui.views.copy.CopyView;

public class InformationView extends MaterialCardView implements CopyView {

    private TextView titleView;
    private TextView informationView;
    private ImageView iconView;
    private TextView requiredView;

    @LayoutRes
    private int layoutDialog;

    private CheckableImageButton endButton;

    private String title;

    private boolean applyEmpties;
    private boolean printRequired;
    private boolean changeable;
    private boolean copyable;

    private boolean hasOnLongClickListeners;

    private boolean isMoving;

    private boolean featureDisabled;

    private boolean initiated;

    private OnInformationChangedListener onInformationChangedListener;
    private BaseDialogBuilder.OnViewCreatedListener onViewCreatedListener;


    private Information information;

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

        information = new Information();
        information.setText(a.getString(R.styleable.InformationView_information));
        information.setInputType(a.getInteger(R.styleable.InformationView_android_inputType, InputType.TYPE_CLASS_TEXT));
        information.setMaxLength(a.getInteger(R.styleable.InformationView_android_maxLength, -1));

        layoutDialog = a.getResourceId(R.styleable.InformationView_dialogLayout, 0);

        setFocusable(false);

        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_information_view, this, true);

        endButton = view.findViewById(R.id.imageButton);
        setOnEndButtonClickListener(v -> {
            information.setMasked(!information.isMasked());
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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        setContentEnabled((ViewGroup) getContent(), enabled);
    }

    private void setContentEnabled(ViewGroup viewGroup, boolean enabled){
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            child.setEnabled(enabled);
            if(child instanceof ViewGroup)
                setContentEnabled((ViewGroup) child, enabled);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(isMoving)
            return true;

        return super.onTouchEvent(event);
    }

    public void setOnChangedListener(OnInformationChangedListener onInformationChangedListener) {
        this.onInformationChangedListener = onInformationChangedListener;
    }

    public void setOnViewCreatedListener(BaseDialogBuilder.OnViewCreatedListener onViewCreatedListener) {
        this.onViewCreatedListener = onViewCreatedListener;
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

        CharSequence transformation = information.getTransformationMethod().getTransformation(information.text, informationView);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < transformation.length(); i++){
            builder.append(transformation.charAt(i));
        }

        return builder.toString();
    }

    public void setTitle(@StringRes int titleId) {
        setTitle(getContext().getString(titleId));
    }

    public void setTitle(String title){
        this.title = title;
        String modifiedTitle = title +(printRequired ? "*" : "");
        titleView.setText(modifiedTitle);
        information.setHint(title);
    }

    public void setInformationText(String text){
        this.information.setText(text);
        handleEndIconVisibility();
        transformString();
    }

    public void setInformationText(@StringRes int stringRes){
        setInformationText(getContext().getString(stringRes));
    }

    public Information getInformation() {
        return information;
    }

    public void setInformationTextColor(@ColorInt int color){
        informationView.setTextColor(color);
    }

    private void handleEndIconVisibility(){
        endButton.setVisibility(information.isSecret() && isInformationSet() ? VISIBLE : GONE);
    }

    public void setSecret(boolean secret) {
        handleEndIconVisibility();
        this.information.setSecret(secret);
        transformString();
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

        if(information.isMasked() && isInformationSet()) informationView.setText(getDotString());
        else informationView.setText(!isInformationSet() ? getContext().getString(R.string.no_value_set) : information.getText());
    }

    private boolean isInformationSet(){
        return !(information.getText() == null || information.getText().trim().isEmpty());
    }

    public void setEndButtonDrawable(Drawable drawable){
        endButton.setVisibility(drawable == null ? GONE : VISIBLE);
        endButton.setImageDrawable(drawable);
    }

    public void setOnEndButtonClickListener(OnClickListener listener){
        endButton.setOnClickListener(listener);
    }

    public void setTransformationMethod(TransformationMethod transformationMethod) {
        this.information.setTransformationMethod(transformationMethod);
        transformString();
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

        new EditDialogBuilder(getContext())
                .setTitle(getContext().getString(R.string.change_param, titleView.getText().toString()))
                .setPositiveButton(R.string.text_continue, (dialog, i, newText) -> {
                    dialog.dismiss();
                    if(!applyEmpties && newText.trim().isEmpty())
                        return;

                    setInformationText(newText);
                    if(onInformationChangedListener != null)
                        onInformationChangedListener.onInformationChanged(newText);
                })
                .withInformation(information)
                .withStartIcon(iconView.getDrawable())
                .withAdditionalCustomLayout(layoutDialog)
                .withOnViewCreatedListener(onViewCreatedListener).show();
    }

    @Override
    public String getCopyString() {
        return information.getText();
    }

    public static class Information{

        private String hint;
        private String text;
        private boolean secret;
        private boolean masked;

        private int maxLength = -1;

        private int inputType;

        private TransformationMethod transformationMethod = PasswordTransformationMethod.getInstance();

        public int getInputType() {
            return inputType;
        }

        public void setInputType(int inputType) {
            this.inputType = inputType;
        }

        public TransformationMethod getTransformationMethod() {
            return transformationMethod;
        }

        public void setTransformationMethod(TransformationMethod transformationMethod) {
            this.transformationMethod = transformationMethod;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }

        @Nullable
        public String getHint() {
            return hint;
        }

        public void setHint(@Nullable String hint) {
            this.hint = hint;
        }

        @Nullable
        public String getText() {
            return text;
        }

        public void setText(@Nullable String text) {
            this.text = text;
        }

        public boolean isSecret() {
            return secret;
        }

        public void setSecret(boolean secret) {
            this.secret = secret;
            this.masked = true;
        }

        public boolean isMasked() {
            return secret && masked;
        }

        public void setMasked(boolean masked) {
            this.masked = masked;

            if(masked && !secret)
                this.secret = true;
        }
    }

    public interface OnInformationChangedListener {
        void onInformationChanged(String informationText);
    }
}