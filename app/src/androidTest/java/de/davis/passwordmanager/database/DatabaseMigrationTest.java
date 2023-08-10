package de.davis.passwordmanager.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static de.davis.passwordmanager.database.SecureElementDatabase.DB_NAME;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import de.davis.passwordmanager.database.converter.Converters;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.password.PasswordDetails;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {

    @Rule
    public MigrationTestHelper helper;
    private static final String TITLE = "passwordElement";
    private static final String PASSWORD = "password";
    private static final String ORIGIN = "origin";
    private static final String USERNAME = "username";

    public DatabaseMigrationTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                SecureElementDatabase.class, List.of(new SecureElementDatabase.TimestampSpec()));
    }

    @Test
    public void testAllMigrations() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB_NAME, 1);
        ContentValues contentValues = new ContentValues();
        contentValues.put("title", TITLE);
        contentValues.put("type", SecureElement.TYPE_PASSWORD);
        contentValues.put("data", Converters.convertDetails(new PasswordDetails(PASSWORD, ORIGIN, USERNAME)));

        db.insert("SecureElement", SQLiteDatabase.CONFLICT_REPLACE, contentValues);
        db.close();


        SecureElementDatabase migratedDB = SecureElementDatabase.createAndGet(InstrumentationRegistry.getInstrumentation().getTargetContext());
        migratedDB.getOpenHelper().getWritableDatabase();

        SecureElement element = migratedDB.getSecureElementDao().getById(1);
        assertEquals(TITLE, element.getTitle());
        assertFalse(element.isFavorite());
        assertNull(element.getModifiedAt());
        assertNotNull(element.getCreatedAt());

        assertEquals(PASSWORD, ((PasswordDetails)element.getDetail()).getPassword());
        assertEquals(ORIGIN, ((PasswordDetails)element.getDetail()).getOrigin());
        assertEquals(USERNAME, ((PasswordDetails)element.getDetail()).getUsername());

        db.close();
    }
}
