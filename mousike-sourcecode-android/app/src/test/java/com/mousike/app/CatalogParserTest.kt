package com.mousike.app
import com.mousike.app.data.remote.YouTubeMusicClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class CatalogParserTest {
 @Test fun albumOverlayDoesNotTurnAlbumIntoVideo(){val j=JSONObject("""{"items":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Real Album"}]},"subtitle":{"runs":[{"text":"The Artist"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"MPREabc"}},"overlay":{"watchEndpoint":{"videoId":"abcdefghijk"}}}}]}""");val p=YouTubeMusicClient().extract(j);assertEquals(1,p.albums.size);assertTrue(p.tracks.isEmpty())}
 @Test fun fixedColumnDuration(){val j=JSONObject("""{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"abcdefghijk"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song Name"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}}]}}}],"fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:12"}]}}}]}}""");val t=YouTubeMusicClient().extract(j).tracks.single();assertEquals(192,t.durationSec);assertEquals("Artist",t.artist)}
 @Test fun emptyRenderer(){assertTrue(YouTubeMusicClient().extract(JSONObject("{}")).tracks.isEmpty())}
 @Test fun realCatalogResponseContainsPlayableMusic(){val input=javaClass.getResourceAsStream("/ytmusic-search.json")!!.bufferedReader().readText();val page=YouTubeMusicClient().extract(JSONObject(input));assertTrue(page.tracks.size>=10);assertTrue(page.tracks.all {it.videoId.matches(Regex("[A-Za-z0-9_-]{11}"))});assertTrue(page.tracks.any {it.durationSec>0})}
 @Test fun albumAndArtistReadThumbnailArrayElements(){
  val json=JSONObject("""{"items":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Artist"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}},"thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://yt3.ggpht.com/small","width":88},{"url":"https://yt3.ggpht.com/portrait","width":544}]}}}}},{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Album"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"MPREalbum"}},"thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"//lh3.googleusercontent.com/cover","width":300}]}}}}}]}""")
  val result=YouTubeMusicClient().extract(json)
  assertEquals("https://yt3.ggpht.com/portrait",result.artists.single().avatarUrl)
  assertEquals("https://lh3.googleusercontent.com/cover",result.albums.single().coverUrl)
 }
 @Test fun realCatalogReadsThumbnailsInsteadOfBlackCards(){
  val root=JSONObject(javaClass.getResourceAsStream("/ytmusic-search.json")!!.bufferedReader().readText())
  val page=YouTubeMusicClient().extract(root)
  assertTrue(page.tracks.isNotEmpty())
  assertTrue(page.tracks.any {it.coverUrl.contains("googleusercontent") || it.coverUrl.contains("ggpht")})
 }
}
