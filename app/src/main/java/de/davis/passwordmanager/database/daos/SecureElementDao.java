package de.davis.passwordmanager.database.daos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import org.reactivestreams.Publisher;

import java.util.List;

import de.davis.passwordmanager.security.element.SecureElement;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

@Dao
public abstract class SecureElementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAll(SecureElement... element);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(SecureElement element);

    @Update
    public abstract void update(SecureElement element);

    @Delete
    public abstract void delete(SecureElement element);

    @Delete
    public abstract void delete(SecureElement... element);

    @Query("SELECT * FROM SecureElement ORDER BY ROWID ASC")
    public abstract LiveData<List<SecureElement>> getAll();

    @Query("SELECT * FROM SecureElement WHERE type = :type ORDER BY ROWID ASC")
    public abstract Single<List<SecureElement>> getAllByType(@SecureElement.ElementType int type);

    @Query("SELECT * FROM SecureElement WHERE title LIKE :title ORDER BY ROWID ASC")
    public abstract List<SecureElement> getByTitle(String title);

    @Query("SELECT count(*) FROM SecureElement")
    public abstract Single<Integer> count();
}
