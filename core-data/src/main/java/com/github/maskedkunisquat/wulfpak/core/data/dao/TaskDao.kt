package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE personId = :personId ORDER BY dueAt ASC NULLS LAST")
    fun getForPerson(personId: UUID): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE personId IS NULL ORDER BY dueAt ASC NULLS LAST")
    fun getStandalone(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY dueAt ASC NULLS LAST")
    fun getPending(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: UUID): Task?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
