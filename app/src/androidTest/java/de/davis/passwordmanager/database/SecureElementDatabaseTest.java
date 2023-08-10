package de.davis.passwordmanager.database;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import de.davis.passwordmanager.database.daos.MasterPasswordDao;
import de.davis.passwordmanager.database.daos.SecureElementDao;
import de.davis.passwordmanager.security.Cryptography;
import de.davis.passwordmanager.security.MasterPassword;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.creditcard.CreditCardDetails;
import de.davis.passwordmanager.security.element.creditcard.Name;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.utils.GeneratorUtil;

import static org.junit.Assert.*;

import com.google.gson.Gson;

@RunWith(AndroidJUnit4.class)
public class SecureElementDatabaseTest {

    private SecureElementDatabase db;

    private MasterPasswordDao masterPasswordDao;
    private SecureElementDao secureElementDao;

    @Before
    public void createDB() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, SecureElementDatabase.class).build();
        masterPasswordDao = db.getMasterPasswordDao();
        secureElementDao = db.getSecureElementDao();
    }

    @Test
    public void testInsertAndRetrievePasswordElement() throws NoSuchFieldException, IllegalAccessException {
        SecureElement passwordElement = new SecureElement(
        new PasswordDetails("password", "origin", "username"),
                "Password Element");


        testWriteRead(passwordElement);
    }

    @Test
    public void testInsertAndRetrieveCreditCardElement() throws NoSuchFieldException, IllegalAccessException {
        SecureElement passwordElement = new SecureElement(
                new CreditCardDetails(Name.fromFullName("cardholder"), "05/12", "0000000000000000", "222"),
                "Credit Card Element");


        testWriteRead(passwordElement);
    }

    @Test
    public void testMasterPasswordOperations(){
        String password = GeneratorUtil.generatePassword(15_000, GeneratorUtil.USE_DIGITS |
                GeneratorUtil.USE_LOWERCASE |
                GeneratorUtil.USE_PUNCTUATION |
                GeneratorUtil.USE_UPPERCASE);

        masterPasswordDao.getOne().test().assertNoValues().dispose();

        masterPasswordDao.insert(new MasterPassword(Cryptography.bcrypt(password))).test()
                .assertComplete()
                .assertNoErrors()
                .dispose();

        masterPasswordDao.getOne().test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(pwd -> Cryptography.checkBcryptHash(password, pwd.getHash()))
                .dispose();
    }

    private void testWriteRead(SecureElement element) throws NoSuchFieldException, IllegalAccessException {
        long id = secureElementDao.insert(element);

        SecureElement storedElement = secureElementDao.getById(id);

        Field field = element.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.setLong(element, id);

        assertEquals(new Gson().toJson(element), new Gson().toJson(storedElement));
    }

    @After
    public void cleanUp(){
        db.close();
    }
}
