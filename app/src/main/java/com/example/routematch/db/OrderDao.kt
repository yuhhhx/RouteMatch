package com.example.routematch.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders ORDER BY receivedTime DESC")
    fun getAllOrders(): Flow<List<OrderInfo>>

    @Query("SELECT * FROM orders WHERE isMatched = 1 ORDER BY matchScore DESC, receivedTime DESC")
    fun getMatchedOrders(): Flow<List<OrderInfo>>

    @Query("SELECT * FROM orders WHERE isAnnounced = 0 ORDER BY receivedTime ASC LIMIT 1")
    suspend fun getUnannouncedOrder(): OrderInfo?

    @Query("SELECT COUNT(*) FROM orders WHERE isMatched = 1")
    fun getMatchedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE isMatched = 1")
    suspend fun getMatchedCountSync(): Int

    @Query("SELECT * FROM orders ORDER BY receivedTime DESC")
    suspend fun getAllOrdersSync(): List<OrderInfo>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: Long): OrderInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderInfo): Long

    @Update
    suspend fun update(order: OrderInfo)

    @Delete
    suspend fun delete(order: OrderInfo)

    @Query("DELETE FROM orders")
    suspend fun deleteAll()

    @Query("DELETE FROM orders WHERE isMatched = 0 AND receivedTime < :cutoffTime")
    suspend fun deleteUnmatchedOlderThan(cutoffTime: Long)
}
