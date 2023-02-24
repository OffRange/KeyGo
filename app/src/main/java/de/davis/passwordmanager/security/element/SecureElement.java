package de.davis.passwordmanager.security.element;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.IntDef;
import androidx.annotation.StringRes;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import net.greypanther.natsort.SimpleNaturalComparator;

import java.io.Serializable;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dashboard.Item;

@Entity
public class SecureElement implements Serializable, Comparable<SecureElement>, Item {

    private static final long serialVersionUID = 4927550289788049498L;

    public static final int TYPE_PASSWORD = 0x1;
    public static final int TYPE_CREDIT_CARD = 0x11;

    @IntDef({TYPE_PASSWORD, TYPE_CREDIT_CARD})
    public @interface ElementType{}


    @ColumnInfo(name = "data")
    private ElementDetail detail;
    private String title;

    @ElementType
    private int type;

    @PrimaryKey(autoGenerate = true)
    private long id;

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


    public SecureElement(ElementDetail detail, String title, long id, @ElementType int type) {
        this.detail = detail;
        this.title = title;
        this.id = id;
        this.type = type;
    }

    @Ignore
    public SecureElement(ElementDetail detail, String title) {
        setDetail(detail);
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

    public ElementDetail getDetail() {
        return detail;
    }

    public void setDetail(ElementDetail detail) {
        this.detail = detail;
        this.type = detail.getType();
    }

    public String getUniqueString(){
        return getId() + title + detail;
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

    @Override
    public int compareTo(SecureElement o) {
        return SimpleNaturalComparator.getInstance().compare(getTitle().toLowerCase(), o.getTitle().toLowerCase());
    }

    public static SecureElement createEmpty(){
        return new SecureElement();
    }
}
