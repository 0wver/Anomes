package com.example.anomess.media

import android.content.Context
import coil.decode.DataSource
import coil.fetch.Fetcher
import coil.fetch.FetchResult
import coil.fetch.SourceResult
import coil.request.Options
import coil.ImageLoader
import coil.decode.ImageSource
import com.example.anomess.security.MediaCrypter
import okio.buffer
import okio.source
import java.io.File

class EncryptedFileFetcher(
    private val file: File,
    private val options: Options,
    private val crypter: MediaCrypter
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // use okio buffer for the stream
        val source = crypter.getInputStream(file).source().buffer()
        
        return SourceResult(
            source = ImageSource(source, options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context, private val crypter: MediaCrypter) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only handle files in our media directory
            if (data.absolutePath.contains("/media/")) {
                return EncryptedFileFetcher(data, options, crypter)
            }
            return null
        }
    }
}
