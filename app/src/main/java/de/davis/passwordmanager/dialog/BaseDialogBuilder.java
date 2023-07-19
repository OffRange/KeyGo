package de.davis.passwordmanager.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;

import androidx.annotation.ArrayRes;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

@SuppressWarnings("unchecked")
public abstract class BaseDialogBuilder<R extends BaseDialogBuilder<R>> extends MaterialAlertDialogBuilder {

    private OnViewCreatedListener onViewCreatedListener;

    public BaseDialogBuilder(@NonNull Context context) {
        super(context);
    }

    public BaseDialogBuilder(@NonNull Context context, int overrideThemeResId) {
        super(context, overrideThemeResId);
    }

    @Override
    public AlertDialog show() {
        View view = onCreateView(LayoutInflater.from(getContext()));
        if(onViewCreatedListener != null)
            onViewCreatedListener.onViewCreated(view);

        setView(view);

        return super.show();
    }

    public abstract View onCreateView(LayoutInflater inflater);


    public R withOnViewCreatedListener(OnViewCreatedListener onViewCreatedListener){
        this.onViewCreatedListener = onViewCreatedListener;
        return (R) this;
    }

    @NonNull
    @Override
    public R setBackground(@Nullable Drawable background) {
        super.setBackground(background);
        return (R) this;
    }

    @NonNull
    @Override
    public R setBackgroundInsetStart(@Px int backgroundInsetStart) {
        super.setBackgroundInsetStart(backgroundInsetStart);
        return (R) this;
    }

    @NonNull
    @Override
    public R setBackgroundInsetTop(@Px int backgroundInsetTop) {
        super.setBackgroundInsetTop(backgroundInsetTop);
        return (R) this;
    }

    @NonNull
    @Override
    public R setBackgroundInsetEnd(@Px int backgroundInsetEnd) {
        super.setBackgroundInsetEnd(backgroundInsetEnd);
        return (R) this;
    }

    @NonNull
    public R setBackgroundInsetBottom(@Px int backgroundInsetBottom) {
        super.setBackgroundInsetBottom(backgroundInsetBottom);
        return (R) this;
    }

    @NonNull
    @Override
    public R setTitle(@StringRes int titleId) {
        return (R) super.setTitle(titleId);
    }

    @NonNull
    @Override
    public R setTitle(@Nullable CharSequence title) {
        return (R) super.setTitle(title);
    }

    @NonNull
    @Override
    public R setCustomTitle(@Nullable View customTitleView) {
        return (R) super.setCustomTitle(customTitleView);
    }

    @NonNull
    @Override
    public R setMessage(@StringRes int messageId) {
        return (R) super.setMessage(messageId);
    }

    @NonNull
    @Override
    public R setMessage(@Nullable CharSequence message) {
        return (R) super.setMessage(message);
    }

    @NonNull
    @Override
    public R setIcon(@DrawableRes int iconId) {
        return (R) super.setIcon(iconId);
    }

    @NonNull
    @Override
    public R setIcon(@Nullable Drawable icon) {
        return (R) super.setIcon(icon);
    }

    @NonNull
    @Override
    public R setIconAttribute(@AttrRes int attrId) {
        return (R) super.setIconAttribute(attrId);
    }

    @NonNull
    @Override
    public R setPositiveButton(
            @StringRes int textId, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setPositiveButton(textId, listener);
    }

    @NonNull
    @Override
    public R setPositiveButton(
            @Nullable CharSequence text, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setPositiveButton(text, listener);
    }

    @NonNull
    @Override
    public R setPositiveButtonIcon(@Nullable Drawable icon) {
        return (R) super.setPositiveButtonIcon(icon);
    }

    @NonNull
    @Override
    public R setNegativeButton(
            @StringRes int textId, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setNegativeButton(textId, listener);
    }

    @NonNull
    @Override
    public R setNegativeButton(
            @Nullable CharSequence text, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setNegativeButton(text, listener);
    }

    @NonNull
    @Override
    public R setNegativeButtonIcon(@Nullable Drawable icon) {
        return (R) super.setNegativeButtonIcon(icon);
    }

    @NonNull
    @Override
    public R setNeutralButton(
            @StringRes int textId, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setNeutralButton(textId, listener);
    }

    @NonNull
    @Override
    public R setNeutralButton(
            @Nullable CharSequence text, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setNeutralButton(text, listener);
    }

    @NonNull
    @Override
    public R setNeutralButtonIcon(@Nullable Drawable icon) {
        return (R) super.setNeutralButtonIcon(icon);
    }

    @NonNull
    @Override
    public R setCancelable(boolean cancelable) {
        return (R) super.setCancelable(cancelable);
    }

    @NonNull
    @Override
    public R setOnCancelListener(
            @Nullable DialogInterface.OnCancelListener onCancelListener) {
        return (R) super.setOnCancelListener(onCancelListener);
    }

    @NonNull
    @Override
    public R setOnDismissListener(
            @Nullable DialogInterface.OnDismissListener onDismissListener) {
        return (R) super.setOnDismissListener(onDismissListener);
    }

    @NonNull
    @Override
    public R setOnKeyListener(@Nullable DialogInterface.OnKeyListener onKeyListener) {
        return (R) super.setOnKeyListener(onKeyListener);
    }

    @NonNull
    @Override
    public R setItems(
            @ArrayRes int itemsId, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setItems(itemsId, listener);
    }

    @NonNull
    @Override
    public R setItems(
            @Nullable CharSequence[] items, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setItems(items, listener);
    }

    @NonNull
    @Override
    public R setAdapter(
            @Nullable final ListAdapter adapter, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setAdapter(adapter, listener);
    }

    @NonNull
    @Override
    public R setCursor(
            @Nullable final Cursor cursor,
            @Nullable final DialogInterface.OnClickListener listener,
            @NonNull String labelColumn) {
        return (R) super.setCursor(cursor, listener, labelColumn);
    }

    @NonNull
    @Override
    public R setMultiChoiceItems(
            @ArrayRes int itemsId,
            @Nullable boolean[] checkedItems,
            @Nullable final DialogInterface.OnMultiChoiceClickListener listener) {
        return (R) super.setMultiChoiceItems(itemsId, checkedItems, listener);
    }

    @NonNull
    @Override
    public R setMultiChoiceItems(
            @Nullable CharSequence[] items,
            @Nullable boolean[] checkedItems,
            @Nullable final DialogInterface.OnMultiChoiceClickListener listener) {
        return (R) super.setMultiChoiceItems(items, checkedItems, listener);
    }

    @NonNull
    @Override
    public R setMultiChoiceItems(
            @Nullable Cursor cursor,
            @NonNull String isCheckedColumn,
            @NonNull String labelColumn,
            @Nullable final DialogInterface.OnMultiChoiceClickListener listener) {
        return (R)
                super.setMultiChoiceItems(cursor, isCheckedColumn, labelColumn, listener);
    }

    @NonNull
    @Override
    public R setSingleChoiceItems(
            @ArrayRes int itemsId, int checkedItem, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setSingleChoiceItems(itemsId, checkedItem, listener);
    }

    @NonNull
    @Override
    public R setSingleChoiceItems(
            @Nullable Cursor cursor,
            int checkedItem,
            @NonNull String labelColumn,
            @Nullable final DialogInterface.OnClickListener listener) {
        return (R)
                super.setSingleChoiceItems(cursor, checkedItem, labelColumn, listener);
    }

    @NonNull
    @Override
    public R setSingleChoiceItems(
            @Nullable CharSequence[] items, int checkedItem, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setSingleChoiceItems(items, checkedItem, listener);
    }

    @NonNull
    @Override
    public R setSingleChoiceItems(
            @Nullable ListAdapter adapter, int checkedItem, @Nullable final DialogInterface.OnClickListener listener) {
        return (R) super.setSingleChoiceItems(adapter, checkedItem, listener);
    }

    @NonNull
    @Override
    public R setOnItemSelectedListener(
            @Nullable final AdapterView.OnItemSelectedListener listener) {
        return (R) super.setOnItemSelectedListener(listener);
    }

    @NonNull
    @Override
    public R setView(int layoutResId) {
        return (R) super.setView(layoutResId);
    }

    @NonNull
    @Override
    public R setView(@Nullable View view) {
        return (R) super.setView(view);
    }


    public interface OnViewCreatedListener {
        void onViewCreated(View view);
    }
}
