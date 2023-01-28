package de.davis.passwordmanager.database.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import de.davis.passwordmanager.security.MasterPassword;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

@Dao
public interface MasterPasswordDao {

    @Insert
    Completable insert(MasterPassword masterPassword);

    @Query("SELECT * FROM MasterPassword ORDER BY ROWID ASC LIMIT 1")
    Maybe<MasterPassword> getOne();
}
