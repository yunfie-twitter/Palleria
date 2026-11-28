package com.yunfie.illustia.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {
    @Test
    fun stripsTagsAndCollapsesSpaces() {
        assertEquals("初音ミク かわいい", stripHtmlTags("<b>初音ミク</b>  <i>かわいい</i>"))
    }

    @Test
    fun decodesNamedEntities() {
        assertEquals("a & b < c > d \"e\" 'f'", stripHtmlTags("a &amp; b &lt; c &gt; d &quot;e&quot; &apos;f&apos;"))
    }

    @Test
    fun decodesNumericEntities() {
        assertEquals("AB", stripHtmlTags("&#65;&#x42;"))
    }

    @Test
    fun keepsLineBreaks() {
        assertEquals("one\ntwo", stripHtmlTags("one\ntwo"))
    }

    @Test
    fun leavesUnknownEntitiesAlone() {
        assertEquals("&unknown;", stripHtmlTags("&unknown;"))
    }
}
