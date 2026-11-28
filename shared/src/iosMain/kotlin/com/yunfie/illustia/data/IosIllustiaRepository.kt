package com.yunfie.illustia.data

import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.platform.FileSystem
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS 側の [IllustiaRepositoryCore] 実装。
 *
 * HTTP エンジンだけがプラットフォーム固有（Ktor Darwin）なので、ここで差し替える。
 * それ以外のオーケストレーションは共通コアのまま。
 */
class IosIllustiaRepository(
    persistence: RepositoryPersistence,
    fileSystem: FileSystem,
    private val ugoiraArchive: UgoiraArchive,
) : IllustiaRepositoryCore(
        persistence = persistence,
        fileSystem = fileSystem,
        engineFactory = { _ -> Darwin.create() },
    ) {
    private val ugoiraCache = UgoiraCacheCommon(fileSystem, ugoiraArchive)

    override suspend fun prepareUgoira(
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback = ugoiraCache.prepare(httpClient, defaultSigner(), url, cacheDir, frames)
}
