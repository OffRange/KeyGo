package de.davis.keygo.feature.backup.di

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.work.WorkManager
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupArkData
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJobs
import de.davis.keygo.feature.backup.di.annotation.BackupArkQualifier
import de.davis.keygo.feature.backup.di.annotation.BackupJobsQualifier
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.io.InputStream
import java.io.OutputStream

@Module
@Configuration
@ComponentScan("de.davis.keygo.feature.backup")
object FeatureBackupModule {

    private val Context.backupJobsDataStore by dataStore(
        "backup_jobs.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoBackupJobs.getDefaultInstance(),
            parser = ProtoBackupJobs.parser()
        )
    )

    @Single
    @BackupJobsQualifier
    internal fun provideBackupJobsDataStore(context: Context) =
        context.backupJobsDataStore

    private val Context.backupArkDataStore by dataStore(
        "backup_ark_data.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoBackupArkData.getDefaultInstance(),
            parser = ProtoBackupArkData.parser(),
        ),
    )

    @Single
    @BackupArkQualifier
    internal fun provideBackupArkDataStore(context: Context) =
        context.backupArkDataStore

    @Single
    internal fun provideWorkManager(context: Context): WorkManager =
        WorkManager.getInstance(context)
}

internal class DefaultProtoSerializer<T : MessageLite>(
    private val defaultInstance: T,
    private val parser: Parser<T>
) : Serializer<T> {

    override val defaultValue: T
        get() = defaultInstance

    override suspend fun readFrom(input: InputStream): T =
        parser.parseFrom(input)

    override suspend fun writeTo(t: T, output: OutputStream) {
        t.writeTo(output)
    }
}