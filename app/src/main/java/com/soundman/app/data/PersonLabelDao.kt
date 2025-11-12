package com.soundman.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonLabelDao {
    @Query("SELECT * FROM person_labels ORDER BY name ASC")
    fun getAllPersons(): Flow<List<PersonLabel>>

    @Query("SELECT * FROM person_labels WHERE id = :id")
    suspend fun getPersonById(id: Long): PersonLabel?

    @Query("SELECT * FROM person_labels WHERE name = :name")
    suspend fun getPersonByName(name: String): PersonLabel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonLabel): Long

    @Update
    suspend fun updatePerson(person: PersonLabel)

    @Delete
    suspend fun deletePerson(person: PersonLabel)

    @Query("UPDATE person_labels SET detectionCount = detectionCount + 1 WHERE id = :id")
    suspend fun incrementDetectionCount(id: Long)
}
