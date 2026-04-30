package com.github.maskedkunisquat.wulfpak.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.ContactDetailDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonRelationshipDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
import com.github.maskedkunisquat.wulfpak.core.data.db.AppTypeConverters
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ActivityParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.PersonRelationship
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        Person::class,
        ContactDetail::class,
        LifeEvent::class,
        Interaction::class,
        InteractionParticipant::class,
        Activity::class,
        ActivityParticipant::class,
        Note::class,
        Gift::class,
        Task::class,
        PersonRelationship::class,
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun contactDetailDao(): ContactDetailDao
    abstract fun lifeEventDao(): LifeEventDao
    abstract fun interactionDao(): InteractionDao
    abstract fun activityDao(): ActivityDao
    abstract fun noteDao(): NoteDao
    abstract fun giftDao(): GiftDao
    abstract fun taskDao(): TaskDao
    abstract fun personRelationshipDao(): PersonRelationshipDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN company TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN jobTitle TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN cachedSummary TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN summaryGeneratedAt INTEGER")
            }
        }

        fun create(context: Context, key: ByteArray): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "wulfpak.db"
            )
                .openHelperFactory(SupportFactory(key))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
