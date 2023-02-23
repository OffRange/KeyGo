package de.davis.passwordmanager.security.element;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.IntDef;
import androidx.annotation.StringRes;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import net.greypanther.natsort.SimpleNaturalComparator;

import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dashboard.Item;
import de.davis.passwordmanager.utils.KeyUtil;

@Entity
public class SecureElement implements Serializable, Comparable<SecureElement>, Item {

    private static final long serialVersionUID = -1504637576007791735L;

    public static final int TYPE_PASSWORD = 0x1;
    public static final int TYPE_CREDIT_CARD = 0x11;

    @IntDef({TYPE_PASSWORD, TYPE_CREDIT_CARD})
    public @interface ElementType{}


    private SealedObject data;
    private String title;

    @ElementType
    private int type;

    @PrimaryKey(autoGenerate = true)
    private long id;

    @Ignore
    private ElementDetail detail;

    public Drawable getIcon(Context context) {
        int px = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8,
                context.getResources().getDisplayMetrics());

        return TextDrawable.builder()
                .beginConfig().bold().endConfig()
                .roundRect(px)
                .build(getTitle().substring(0, getTitle().length() >= 2 ? 2 : 1), ColorGenerator.MATERIAL.getColor(getTitle()));
    }


    public SecureElement(SealedObject data, String title, long id, @ElementType int type) {
        this.data = data;
        this.title = title;
        this.id = id;
        this.type = type;
    }

    @Ignore
    public SecureElement(ElementDetail detail, String title) {
        encrypt(detail);
        this.title = title;
    }

    private SecureElement(){}

    public char getLetter(){
        return Character.toUpperCase(getTitle().charAt(0));
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SealedObject getData() {
        return data;
    }

    protected void setData(SealedObject data) {
        this.data = data;
        detail = null;
    }

    public String getUniqueString(){
        return getId() + title + data;
    }

    @Override
    public long getId() {
        return id;
    }

    @ElementType
    public int getType() {
        return type;
    }

    @StringRes
    public int getTypeName(){
        switch (getType()) {
            case TYPE_PASSWORD:
                return R.string.password;
            case TYPE_CREDIT_CARD:
            default:
                return R.string.credit_card;
        }
    }

    //TODO find better way to save the data in to the database, because it is Serializable and
    // changes to the classes may cause errors and crashes
    public void encrypt(ElementDetail detail){
        type = detail.getType();
        Cipher c = KeyUtil.getCipher();
        try {
            c.init(Cipher.ENCRYPT_MODE, KeyUtil.getSecretKey());
            setData(new SealedObject(detail, c));
        } catch (GeneralSecurityException | IOException ex) {
            ex.printStackTrace();
        }
    }

    public ElementDetail decrypt(){
        try {
            return detail = (ElementDetail) getData().getObject(KeyUtil.getSecretKey());
        } catch (ClassNotFoundException | GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public ElementDetail getDetail() {
        if(detail == null)
            return decrypt();

        return detail;
    }

    public void prepareForDB(){
        if(detail == null)
            return;

        encrypt(detail);
    }

    @Override
    public int compareTo(SecureElement o) {
        return SimpleNaturalComparator.getInstance().compare(getTitle().toLowerCase(), o.getTitle().toLowerCase());
    }

    public static SecureElement createEmpty(){
        return new SecureElement();
    }
}
