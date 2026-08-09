package com.tdcreator.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.tdcreator.core.network.dto.JobStatus
import com.tdcreator.core.network.dto.JobTier
import com.tdcreator.core.network.dto.JobType

@Database(entities = [JobEntity::class, UploadEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun uploadDao(): UploadDao

    companion object {
        const val DATABASE_NAME = "tdcreator.db"
    }
}

class Converters {
    @TypeConverter fun jobStatusToString(v: JobStatus) = v.name
    @TypeConverter fun stringToJobStatus(v: String) = JobStatus.valueOf(v)

    @TypeConverter fun jobTypeToString(v: JobType) = v.name
    @TypeConverter fun stringToJobType(v: String) = JobType.valueOf(v)

    @TypeConverter fun jobTierToString(v: JobTier) = v.name
    @TypeConverter fun stringToJobTier(v: String) = JobTier.valueOf(v)

    @TypeConverter fun uploadStatusToString(v: UploadStatus) = v.name
    @TypeConverter fun stringToUploadStatus(v: String) = UploadStatus.valueOf(v)
}
