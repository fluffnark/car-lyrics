package com.doomslug.carlyrics

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorpheHomeFeedTest {
    @Test fun homeResolvesOnlyExactTitleAndChannelIgnoringPunctuation() {
        val card = HomeCard("A Song — Live!", "The Artist")
        val wrongChannel = KaraokeVideo("abcdefghijk", "A Song — Live!", "Someone Else")
        val wrongTitle = KaraokeVideo("12345678901", "A Song", "The Artist")
        val correct = KaraokeVideo("23456789012", "A Song - Live!", "The Artist")
        assertNull(MorpheHomeFeed.resolve(card, listOf(wrongChannel, wrongTitle)))
        assertEquals(correct, MorpheHomeFeed.resolve(card, listOf(wrongChannel, correct)))
    }
    @Test fun homeRejectsMissingChannelAndDeduplicatesCards() {
        val cards = MorpheHomeFeed.parse("""[{"title":"Hello","channel":"Artist"},{"title":"Hello!","channel":"Artist"},{"title":"Unknown"}]""")
        assertEquals(listOf(HomeCard("Hello", "Artist")), cards)
    }
    @Test fun publicSearchRetainsChannelWithVideoRatherThanCrossPairingMetadata() {
        val html = """var ytInitialData = {"contents":{"videoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"A Song"}]},"ownerText":{"runs":[{"text":"Artist"}]}}}};"""
        assertEquals(listOf(KaraokeVideo("abcdefghijk", "A Song", "Artist")), YouTubeSearchParser.parse(html))
    }
}
