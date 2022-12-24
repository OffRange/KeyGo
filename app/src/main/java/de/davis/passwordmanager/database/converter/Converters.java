package de.davis.passwordmanager.database.converter;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.SealedObject;

public class Converters {

    private static final Gson gson = new Gson();

    @TypeConverter
    public static byte[] sealedObjectToByteArray(SealedObject sealedObject) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        
        ObjectOutput objectOutput = null;
        try {
            objectOutput = new ObjectOutputStream(byteArrayOutputStream);
            objectOutput.writeObject(sealedObject);
            objectOutput.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(objectOutput != null){
                try {
                    objectOutput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    @TypeConverter
    public static SealedObject byteArrayToSealedObject(byte[] content) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content);
        try {
            ObjectInput oi = new ObjectInputStream(byteArrayInputStream);
            Object obj = oi.readObject();
            return (SealedObject) obj;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                byteArrayInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @TypeConverter
    public static String stringListToString(List<String> list){
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<String> stringToStringList(String s){
        if(s == null)
            return Collections.emptyList();

        Type listOfString = new TypeToken<ArrayList<String>>() {}.getType();
        return gson.fromJson(s, listOfString);
    }
}
