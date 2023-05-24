package de.davis.passwordmanager.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.AutoMigrationSpec;
import androidx.sqlite.db.SupportSQLiteDatabase;

import de.davis.passwordmanager.database.converter.Converters;
import de.davis.passwordmanager.database.daos.MasterPasswordDao;
import de.davis.passwordmanager.database.daos.SecureElementDao;
import de.davis.passwordmanager.security.MasterPassword;
import de.davis.passwordmanager.security.element.SecureElement;

@TypeConverters({Converters.class})
@Database(version = 2, entities = {SecureElement.class, MasterPassword.class}, autoMigrations = {@AutoMigration(from = 1, to = 2, spec = SecureElementDatabase.TimestampSpec.class)})
public abstract class SecureElementDatabase extends RoomDatabase {

    public static class TimestampSpec implements AutoMigrationSpec{

        @Override
        public void onPostMigrate(@NonNull SupportSQLiteDatabase db) {
            ContentValues cv = new ContentValues();
            cv.put("created_at", System.currentTimeMillis());
            db.update("SecureElement", SQLiteDatabase.CONFLICT_REPLACE, cv, "created_at is ?", new Object[]{null});
        }
    }

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
