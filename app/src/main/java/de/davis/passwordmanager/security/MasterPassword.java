package de.davis.passwordmanager.security;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import de.davis.passwordmanager.database.SecureElementDatabase;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Entity
public class MasterPassword {

    @PrimaryKey(autoGenerate = true)
    private long id;
    private byte[] hash;

    public MasterPassword(byte[] hash) {
        this.hash = hash;
    }

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public static Maybe<MasterPassword> getOne(){
        return SecureElementDatabase.getInstance().getMasterPasswordDao().getOne()
                .subscribeOn(Schedulers.io()).observeOn(Schedulers.newThread());
    }

    public static Completable set(MasterPassword masterPassword){
        return SecureElementDatabase.getInstance().getMasterPasswordDao().insert(masterPassword)
                .subscribeOn(Schedulers.io()).observeOn(Schedulers.newThread());
    }

    public static Completable set(byte[] hash){
        return set(new MasterPassword(hash));
    }

    public static boolean changeMasterPassword(String plaintext, String newMaster){
        MasterPassword masterPassword = getOne().blockingGet();
        if(masterPassword != null && !Cryptography.checkBcryptHash(plaintext, masterPassword.getHash()))
            return false;

        set(Cryptography.bcrypt(newMaster)).subscribe();
        return true;
    }
}
