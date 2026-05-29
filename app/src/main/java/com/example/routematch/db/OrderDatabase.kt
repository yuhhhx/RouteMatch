package com.example.routematch.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OrderInfo::class],
    version = 1,
    exportSchema = false
)
abstract class OrderDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var INSTANCE: OrderDatabase? = null

        fun getInstance(context: Context): OrderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): OrderDatabase {
            // SECURITY: Database stored in app's internal private directory
            // No external storage needed, no encryption at DB level
            // (sensitive fields are individually AES-GCM encrypted via CryptoUtil)
            return Room.databaseBuilder(
                context.applicationContext,
                OrderDatabase::class.java,
                "route_match_orders.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
