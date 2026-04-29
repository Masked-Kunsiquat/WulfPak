package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ContactDetailDao {

    @Query("SELECT * FROM contact_details WHERE personId = :personId")
    fun getForPerson(personId: UUID): Flow<List<ContactDetail>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contactDetail: ContactDetail)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(contactDetails: List<ContactDetail>)

    @Update
    suspend fun update(contactDetail: ContactDetail)

    @Delete
    suspend fun delete(contactDetail: ContactDetail)
}
