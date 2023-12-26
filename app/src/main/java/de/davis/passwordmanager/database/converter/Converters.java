package de.davis.passwordmanager.database.converter;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Date;

import de.davis.passwordmanager.database.entities.details.ElementDetail;
import de.davis.passwordmanager.gson.ElementDetailTypeAdapter;
import de.davis.passwordmanager.security.Cryptography;

public class Converters {

    private static final Gson gson = new GsonBuilder().registerTypeAdapter(ElementDetail.class, new ElementDetailTypeAdapter()).create();

    @TypeConverter
    @Nullable
    public static byte[] convertDetails(ElementDetail elementDetail){
        String json = gson.toJson(elementDetail, ElementDetail.class);
        return Cryptography.encryptAES(json.getBytes());
    }

    @TypeConverter
    public static ElementDetail convertByteArray(byte[] data){
        byte[] decrypted = Cryptography.decryptAES(data);
        return gson.fromJson(new String(decrypted), ElementDetail.class);
    }

    @TypeConverter
    @Nullable
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    @Nullable
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
