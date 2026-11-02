package com.github.petergamez.joox;

import com.github.petergamez.joox.api.JooxProtocolParser;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JooxProtocolParserTest {

    private final JooxProtocolParser parser = new JooxProtocolParser();

    @Test
    void testParseSingleTrack() throws IOException {
        String json = "{\n" +
                "  \"songid\": \"song_12345\",\n" +
                "  \"song_name\": \"Hati-Hati di Jalan\",\n" +
                "  \"singer_name\": \"Tulus\",\n" +
                "  \"play_duration\": 242,\n" +
                "  \"album_pic\": \"https://img.joox.com/pic/123.jpg\"\n" +
                "}";

        AudioTrackInfo info = parser.parseTrack(json, "fallback_id");

        assertEquals("song_12345", info.identifier);
        assertEquals("Hati-Hati di Jalan", info.title);
        assertEquals("Tulus", info.author);
        assertEquals(242000L, info.length);
        assertEquals("https://img.joox.com/pic/123.jpg", info.artworkUrl);
    }

    @Test
    void testParseSearchResults() throws IOException {
        String json = "{\n" +
                "  \"itemlist\": [\n" +
                "    {\n" +
                "      \"songid\": \"s1\",\n" +
                "      \"song_name\": \"Song One\",\n" +
                "      \"singer_name\": \"Artist A\",\n" +
                "      \"play_duration\": 180\n" +
                "    },\n" +
                "    {\n" +
                "      \"songid\": \"s2\",\n" +
                "      \"song_name\": \"Song Two\",\n" +
                "      \"singer_name\": \"Artist B\",\n" +
                "      \"play_duration\": 200\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        AudioPlaylist playlist = parser.parseSearch(json, DummyAudioTrack::new);

        assertNotNull(playlist);
        assertTrue(playlist.isSearchResult());
        assertEquals(2, playlist.getTracks().size());
        assertEquals("Song One", playlist.getTracks().get(0).getInfo().title);
        assertEquals("Song Two", playlist.getTracks().get(1).getInfo().title);
    }

    @Test
    void testParseNextDataSearch() throws IOException {
        String html = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
                "{\"props\":{\"pageProps\":{\"searchSections\":{\"songs\":{\"item_list\":[" +
                "{\"type\":5,\"song\":[{\"song_info\":{\"id\":\"qrc123\",\"name\":\"ความรัก\",\"artist_list\":[{\"name\":\"Bodyslam\"}],\"play_duration\":291,\"images\":[{\"url\":\"https://img/cover.jpg\"}]}}]}," +
                "{\"type\":5,\"song\":[{\"song_info\":{\"id\":\"qrc456\",\"name\":\"แสงสุดท้าย\",\"artist_list\":[{\"name\":\"Bodyslam\"}],\"play_duration\":275,\"images\":[{\"url\":\"https://img/cover2.jpg\"}]}}]}" +
                "]}}}}}</script></head><body></body></html>";

        AudioPlaylist playlist = parser.parseNextDataSearch(html, DummyAudioTrack::new);

        assertNotNull(playlist);
        assertTrue(playlist.isSearchResult());
        assertEquals(2, playlist.getTracks().size());

        AudioTrackInfo track1 = playlist.getTracks().get(0).getInfo();
        assertEquals("qrc123", track1.identifier);
        assertEquals("ความรัก", track1.title);
        assertEquals("Bodyslam", track1.author);
        assertEquals(291000L, track1.length);
        assertEquals("https://img/cover.jpg", track1.artworkUrl);

        AudioTrackInfo track2 = playlist.getTracks().get(1).getInfo();
        assertEquals("qrc456", track2.identifier);
        assertEquals("แสงสุดท้าย", track2.title);
        assertEquals(275000L, track2.length);
    }

    @Test
    void testParseNextDataTrack() throws IOException {
        String html = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
                "{\"props\":{\"pageProps\":{" +
                "\"singleInfo\":{\"id\":\"qrc123\",\"title\":\"ความรัก\",\"msinger\":\"Bodyslam\",\"imgSrc\":\"https://img/cover.jpg\"}," +
                "\"passingArgumentsData\":{\"play_duration\":291}" +
                "}}}</script></head><body></body></html>";

        AudioTrackInfo track = parser.parseNextDataTrack(html, "fallback_id");

        assertNotNull(track);
        assertEquals("qrc123", track.identifier);
        assertEquals("ความรัก", track.title);
        assertEquals("Bodyslam", track.author);
        assertEquals(291000L, track.length);
        assertEquals("https://img/cover.jpg", track.artworkUrl);
    }

    @Test
    void testParseNextDataAlbum() throws IOException {
        String html = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
                "{\"props\":{\"pageProps\":{" +
                "\"albumData\":{\"id\":\"alb999\",\"title\":\"คราม\",\"imgSrc\":\"https://img/album.jpg\"," +
                "\"trackList\":{\"items\":[" +
                "{\"id\":\"t1\",\"name\":\"คราม\",\"artist_list\":[{\"name\":\"Bodyslam\"}],\"play_duration\":250}," +
                "{\"id\":\"t2\",\"name\":\"ความรัก\",\"artist_list\":[{\"name\":\"Bodyslam\"}],\"play_duration\":291}" +
                "]}}}}}</script></head><body></body></html>";

        AudioPlaylist album = parser.parseNextDataAlbum(html, "alb999", DummyAudioTrack::new);

        assertNotNull(album);
        assertFalse(album.isSearchResult());
        assertEquals("คราม", album.getName());
        assertEquals(2, album.getTracks().size());
        assertEquals("t1", album.getTracks().get(0).getInfo().identifier);
        assertEquals("คราม", album.getTracks().get(0).getInfo().title);
        assertEquals("t2", album.getTracks().get(1).getInfo().identifier);
        assertEquals("ความรัก", album.getTracks().get(1).getInfo().title);
    }

    @Test
    void testParseNextDataPlaylist() throws IOException {
        String html = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
                "{\"props\":{\"pageProps\":{" +
                "\"playlistDetailList\":{\"id\":\"pl111\",\"title\":\"Best of Bodyslam\",\"imgSrc\":\"https://img/playlist.jpg\"," +
                "\"trackList\":[" +
                "{\"id\":\"p1\",\"name\":\"Track One\",\"artist_list\":[{\"name\":\"Bodyslam\"}],\"play_duration\":210}," +
                "{\"id\":\"p2\",\"name\":\"Track Two\",\"artist_list\":[{\"name\":\"Bodyslam\"}],\"play_duration\":220}" +
                "]}}}}</script></head><body></body></html>";

        AudioPlaylist playlist = parser.parseNextDataPlaylist(html, "pl111", DummyAudioTrack::new);

        assertNotNull(playlist);
        assertFalse(playlist.isSearchResult());
        assertEquals("Best of Bodyslam", playlist.getName());
        assertEquals(2, playlist.getTracks().size());
        assertEquals("p1", playlist.getTracks().get(0).getInfo().identifier);
        assertEquals("p2", playlist.getTracks().get(1).getInfo().identifier);
    }



    @Test
    void testSelectsFullSongOverPreviewInSearch() throws IOException {
        String html = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
                "{\"props\":{\"pageProps\":{\"searchSections\":{\"songs\":{\"item_list\":[" +
                "{\"type\":5,\"song\":[" +
                "  {\"song_info\":{\"id\":\"teaser_id\",\"name\":\"Test Song\",\"artist_list\":[{\"name\":\"Artist\"}],\"play_duration\":60}}," +
                "  {\"song_info\":{\"id\":\"full_id\",\"name\":\"Test Song\",\"artist_list\":[{\"name\":\"Artist\"}],\"play_duration\":217}}" +
                "]}" +
                "]}}}}}</script></head><body></body></html>";

        AudioPlaylist playlist = parser.parseNextDataSearch(html, DummyAudioTrack::new);

        assertNotNull(playlist);
        assertEquals(1, playlist.getTracks().size());
        AudioTrackInfo track = playlist.getTracks().get(0).getInfo();
        assertEquals("full_id", track.identifier);
        assertEquals(217000L, track.length);
    }

    @Test
    void testExtractAlbumIdAndDuration() throws IOException {
        String singleHtml = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
                "{\"props\":{\"pageProps\":{" +
                "\"singleInfo\":{\"id\":\"s123\",\"title\":\"Song Title\"}," +
                "\"passingArgumentsData\":{\"album_id\":\"alb_xyz\",\"play_duration\":30}" +
                "}}}</script></head><body></body></html>";

        String albumId = parser.extractAlbumId(singleHtml);
        assertEquals("alb_xyz", albumId);

        String albumHtml = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
                "{\"props\":{\"pageProps\":{" +
                "\"albumData\":{\"id\":\"alb_xyz\",\"trackList\":{\"items\":[" +
                "  {\"id\":\"s123\",\"name\":\"Song Title\",\"play_duration\":242}," +
                "  {\"id\":\"s456\",\"name\":\"Other Song\",\"play_duration\":180}" +
                "]}}}}}</script></head><body></body></html>";

        long duration = parser.parseTrackDurationFromAlbum(albumHtml, "s123");
        assertEquals(242000L, duration);
    }

    private static class DummyAudioTrack extends com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack {
        public DummyAudioTrack(AudioTrackInfo trackInfo) {
            super(trackInfo);
        }

        @Override
        public void process(com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor executor) {
        }

        @Override
        protected AudioTrack makeShallowClone() {
            return new DummyAudioTrack(trackInfo);
        }

        @Override
        public com.sedmelluq.discord.lavaplayer.source.AudioSourceManager getSourceManager() {
            return null;
        }
    }
}
