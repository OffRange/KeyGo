package de.davis.passwordmanager.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import de.davis.passwordmanager.database.converter.Converters;
import de.davis.passwordmanager.database.daos.MasterPasswordDao;
import de.davis.passwordmanager.database.daos.SecureElementDao;
import de.davis.passwordmanager.security.MasterPassword;
import de.davis.passwordmanager.security.element.SecureElement;

@TypeConverters({Converters.class})
@Database(version = 1, entities = {SecureElement.class, MasterPassword.class})
public abstract class SecureElementDatabase extends RoomDatabase {

    public static final String DB_NAME = "secure_element_database";
    private static SecureElementDatabase database;

    public abstract MasterPasswordDao getMasterPasswordDao();

    public abstract SecureElementDao getSecureElementDao();

    public static SecureElementDatabase createAndGet(Context context){
        if(database == null)
            database = Room.databaseBuilder(context, SecureElementDatabase.class, DB_NAME).build();

        return database;
    }

    @Override
    public void close() {
        super.close();
        database = null;
    }

    public static SecureElementDatabase getInstance(){
        if(database == null)
            throw new NullPointerException("call createAndGet first");

        return database;
    }
}
