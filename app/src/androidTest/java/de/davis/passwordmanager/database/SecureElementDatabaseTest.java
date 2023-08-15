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
    public void testInsertAndRetrievePasswordElement() {
        String title = "Password Element";
        String password = generateTestPassword();
        String origin = "origin";
        String username = "username";


        SecureElement passwordElement = new SecureElement(
        new PasswordDetails(password, origin, username), title);

        SecureElement element = writeAndRead(passwordElement);
        PasswordDetails details = (PasswordDetails) element.getDetail();

        assertEquals(title, element.getTitle());
        assertEquals(password, details.getPassword());
        assertEquals(origin, details.getOrigin());
        assertEquals(username, details.getUsername());
    }

    @Test
    public void testInsertAndRetrieveCreditCardElement() {
        String title = "Credit Card Element";
        String expirationDate = "05/12";
        String cardNumber = "0000000000000000";
        String cvv = "222";
        Name name = Name.fromFullName("cardholder");

        SecureElement passwordElement = new SecureElement(new CreditCardDetails(name, expirationDate, cardNumber, cvv), title);

        SecureElement element = writeAndRead(passwordElement);
        CreditCardDetails details = (CreditCardDetails) element.getDetail();

        assertEquals(title, element.getTitle());
        assertEquals(name, details.getCardholder());
        assertEquals(expirationDate, details.getExpirationDate());
        assertEquals(cardNumber, details.getCardNumber());
        assertEquals(cvv, details.getCvv());
    }

    @Test
    public void testMasterPasswordOperations(){
        String password = generateTestPassword();

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

    private SecureElement writeAndRead(SecureElement element) {
        long id = secureElementDao.insert(element);

        return secureElementDao.getById(id);
    }

    private String generateTestPassword(){
        return GeneratorUtil.generatePassword(15_000, GeneratorUtil.USE_DIGITS |
                GeneratorUtil.USE_LOWERCASE |
                GeneratorUtil.USE_PUNCTUATION |
                GeneratorUtil.USE_UPPERCASE);
    }

    @After
    public void cleanUp(){
        db.close();
    }
}
