package com.yunfie.illustia.platform

import kotlinx.atomicfu.atomic

/**
 * プラットフォーム非依存のアトミックカウンタ／フラグ。
 *
 * `java.util.concurrent.atomic.AtomicLong` / `AtomicBoolean` は Kotlin/Native に
 * 存在しないため、ここで使う 3 操作だけを小さな型に閉じ込める。
 */
class AtomicCounter(
    initial: Long = 0L,
) {
    private val value = atomic(initial)
    private val flag = atomic(initial != 0L)

    /** 現在値を返す。 */
    fun get(): Long = value.value

    /** 1 増やした後の値を返す。 */
    fun incrementAndGet(): Long = value.incrementAndGet()

    /** 現在値が [expected] と等しいときだけ [update] へ置き換える。 */
    fun compareAndSet(
        expected: Boolean,
        update: Boolean,
    ): Boolean = flag.compareAndSet(expected, update)

    /** フラグとして立てる。 */
    fun setTrue(): Boolean = flag.compareAndSet(expect = false, update = true)
}
