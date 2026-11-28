package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするファイルシステム操作の境界。
 *
 * 従来は各モジュールが `java.io.File` / `FileOutputStream` を直接使っていた。
 * パスは常に文字列で受け渡し、実装側（Android / iOS）にファイル API を閉じ込める。
 */
interface FileSystem {
    /** ディレクトリを再帰的に作成する。 */
    fun makeDirectories(path: String): Boolean

    /** [path] の各エントリ名。存在しない・ディレクトリでない場合は空。 */
    fun list(path: String): List<String>

    /** [path] への書き込み用ストリームを開く。 */
    fun openWrite(path: String): OutputStreamHandle

    /** [path] のファイル情報。存在しない場合は null。 */
    fun stat(path: String): FileEntry?

    /** ファイル／ディレクトリを削除する。ディレクトリは再帰的に削除する。 */
    fun delete(path: String): Boolean

    /** [prefix] と [suffix] を持つ一時ファイルを [directory] に作成し、そのパスを返す。 */
    fun createTempFile(
        prefix: String,
        suffix: String,
        directory: String,
    ): String

    /** [path] がディレクトリか。 */
    fun isDirectory(path: String): Boolean

    /** [sourcePath] の内容を [targetPath] へ複製する。 */
    fun copy(
        sourcePath: String,
        targetPath: String,
    )
}

/** パスの 1 エントリ分の情報。 */
data class FileEntry(
    /** エントリ名（ディレクトリを含まない）。 */
    val name: String,
    /** ディレクトリなら true。 */
    val isDirectory: Boolean,
    /** ファイルサイズ（バイト）。ディレクトリでは 0。 */
    val size: Long,
)

/**
 * 書き込み用ストリームのハンドル。
 *
 * ViewModel 層は生のバイト列を扱わないため、コピー元は常に文字列パスまたは
 * [ByteArray] であり、ここでは `java.io.OutputStream` 相当の最小操作だけを公開する。
 */
interface OutputStreamHandle : AutoCloseable {
    fun write(bytes: ByteArray)

    fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    )
}
