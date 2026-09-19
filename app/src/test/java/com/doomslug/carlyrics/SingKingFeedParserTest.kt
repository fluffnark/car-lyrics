package com.doomslug.carlyrics

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SingKingFeedParserTest {
    @Test fun acceptsValidEntriesAndSkipsDuplicatesAndMalformedIds() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
          <title>Sing King</title>
          <entry><yt:videoId>abcdefghijk</yt:videoId><title>  First Song  </title></entry>
          <entry><yt:videoId>abcdefghijk</yt:videoId><title>Duplicate</title></entry>
          <entry><yt:videoId>bad</yt:videoId><title>Invalid</title></entry>
          <entry><yt:videoId>lmnopqrstuv</yt:videoId><title>Second Song</title></entry>
        </feed>"""
        val videos = SingKingFeedParser.parse(xml.byteInputStream())
        assertEquals(listOf(KaraokeVideo("abcdefghijk", "First Song"), KaraokeVideo("lmnopqrstuv", "Second Song")), videos)
    }
}
