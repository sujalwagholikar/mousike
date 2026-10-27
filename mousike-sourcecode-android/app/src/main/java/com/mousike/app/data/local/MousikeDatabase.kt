package com.mousike.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LikedSongEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        SavedAlbumEntity::class,
        SavedArtistEntity::class,
        FeedbackEntity::class,
        SearchQueryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MousikeDatabase : RoomDatabase() {
    abstract fun dao(): MousikeDao

    companion object {
        @Volatile
        private var INSTANCE: MousikeDatabase? = null

        fun getDatabase(context: Context): MousikeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MousikeDatabase::class.java,
                    "mousike_master.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
