package de.davis.passwordmanager.database.daos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import de.davis.passwordmanager.security.element.SecureElement;
import io.reactivex.rxjava3.core.Single;

@Dao
public abstract class SecureElementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertNew(SecureElement element);

    public void insert(SecureElement element){
        Date date = Date.from(Instant.now());
        element.setCreatedAt(date);
        insertNew(element);
    }

    @Update
    protected abstract void updateElement(SecureElement element);

    public void update(SecureElement element){
        Date date = Date.from(Instant.now());
        element.setModifiedAt(date);
        updateElement(element);
    }

    @Delete
    public abstract void delete(SecureElement element);

    @Delete
    public abstract void delete(SecureElement... element);

    @Query("SELECT * FROM SecureElement ORDER BY ROWID ASC")
    public abstract LiveData<List<SecureElement>> getAll();

    @Query("SELECT * FROM SecureElement WHERE type = :type ORDER BY ROWID ASC")
    public abstract Single<List<SecureElement>> getAllByType(@SecureElement.ElementType int type);

    @Query("SELECT * FROM SecureElement WHERE title LIKE :title ORDER BY ROWID ASC")
    public abstract LiveData<List<SecureElement>> getByTitle(String title);

    @Query("SELECT count(*) FROM SecureElement")
    public abstract Single<Integer> count();

    @Query("SELECT * FROM SecureElement WHERE favorite ORDER BY ROWID ASC LIMIT :limit")
    public abstract LiveData<List<SecureElement>> getFavorites(int limit);

    @Query("SELECT * FROM SecureElement ORDER BY created_at DESC LIMIT :limit")
    public abstract LiveData<List<SecureElement>> getLastCreated(int limit);

    @Query("SELECT * FROM SecureElement WHERE modified_at is not NULL ORDER BY modified_at DESC LIMIT :limit")
    public abstract LiveData<List<SecureElement>> getLastModified(int limit);
}
