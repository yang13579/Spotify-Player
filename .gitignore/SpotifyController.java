package spotifyplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;

public class SpotifyController {

    final static private String SPOTIFY_CLIENT_ID = "d54a3340ac8b4a12b8155a28942cfd6b";
    final static private String SPOTIFY_CLIENT_SECRET = "fb640e7796f94f419f4f9d8ea8c60b04";

    public static String getArtistId(String artistNameQuery) {
        String artistId = "";

        try {
            // TODO - From an artist string, get the spotify ID
            // Recommended Endpoint: https://api.spotify.com/v1/search
            // Parse the JSON output to retrieve the ID

            String endpoint = "https://api.spotify.com/v1/search";
            String params = "type=artist&q=" + artistNameQuery;
            String jsonOutput = spotifyEndpointToJson(endpoint, params);

            JsonObject root = new JsonParser().parse(jsonOutput).getAsJsonObject();

            JsonObject artists = root.get("artists").getAsJsonObject();

            JsonArray items = artists.get("items").getAsJsonArray();

            if (items.size() > 0) {
                JsonObject item = items.get(0).getAsJsonObject();
                artistId = item.get("id").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return artistId;
    }

    public static ArrayList<String> getAlbumIdsFromArtist(String artistId) {
        ArrayList<String> albumIds = new ArrayList<>();
        int offset = 0;
        boolean keepSearching = true;

        while (keepSearching) {
            try {
                String endpoint = "https://api.spotify.com/v1/artists/" + artistId + "/albums";
                String params = "market=CA&limit=50&offset=" + offset;
                String jsonOutput = spotifyEndpointToJson(endpoint, params);

                if (jsonOutput.equals(""))
                {
                    return null;
                }
                
                JsonObject root = new JsonParser().parse(jsonOutput).getAsJsonObject();

                JsonArray items = root.get("items").getAsJsonArray();
                for (int i = 0; i < items.size(); i++) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    albumIds.add(item.get("id").getAsString());
                }

                if (albumIds.size() > offset + 50) {
                    offset += 50;
                } else {
                    keepSearching = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                keepSearching = false;
            }
        }

        return albumIds;
    }

    public static ArrayList<Album> getAlbumDataFromArtist(String artistId) {
        ArrayList<String> albumIds = getAlbumIdsFromArtist(artistId);
        ArrayList<Album> albums = new ArrayList<>();

        if(albumIds.equals(null)){
            return null;
        }
        
        for (String albumId : albumIds) {
            try {
                // TODO - Retrieve all album data from the list of album ids for an artist
                // 
                // You can have a look at the Album class included
                // 
                // Endpoint : https://api.spotify.com/v1/albums/{id}
                // Note:      {id} is the id of the album
                //
                // Arguments - Filter for the CA market

                // Warning!! For the preview_url, the json item can be a string 
                //           or null, below is the code to write for parsing
                // 
                //
                //    if (item.get("preview_url").isJsonNull() == false)
                //    {
                //        previewUrl = item.get("preview_url").getAsString();
                //    }
                String artistName = "";
                String albumName = "";
                String coverURL = "";
                ArrayList<String> trackTitles = new ArrayList<>();
                ArrayList<Integer> trackLengths = new ArrayList<>();

                //trackTitles.add("Song 1");
                //trackLengths.add(218);
                String endpoint = "https://api.spotify.com/v1/albums/" + albumId;
                String params = "market=CA&limit=50";
                String jsonOutput = spotifyEndpointToJson(endpoint, params);

                
                
                JsonObject root = new JsonParser().parse(jsonOutput).getAsJsonObject();
                JsonArray artists = root.get("artists").getAsJsonArray();
                if (artists.size() > 0) {
                    JsonObject artist = artists.get(0).getAsJsonObject();
                    artistName = artist.get("name").getAsString();
                }

                albumName = root.get("name").getAsString();

                JsonArray images = root.get("images").getAsJsonArray();
                JsonObject image = images.get(0).getAsJsonObject();
                coverURL = image.get("url").getAsString();

                JsonObject tracks = root.get("tracks").getAsJsonObject();
                JsonArray items = tracks.get("items").getAsJsonArray();

                ArrayList<Track> albumTracks = new ArrayList<>();

                for (int i = 0; i < items.size(); i++) {
                    JsonObject item = items.get(i).getAsJsonObject();

                    String trackTitle = item.get("name").getAsString();
                    int trackLength = item.get("duration_ms").getAsInt() / 1000;

                    String previewUrl = "";
                    if (item.get("preview_url").isJsonNull() == false) {
                        previewUrl = item.get("preview_url").getAsString();
                    }
                    // track number

                    int trackNumber = item.get("track_number").getAsInt();

                    albumTracks.add(new Track(trackNumber, trackTitle, trackLength, previewUrl));
                }

                albums.add(new Album(artistName, albumName, coverURL, albumTracks));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return albums;
    }

    // This code will help you retrieve the JSON data from a spotify end point
    // It takes care of the complicated parts such as the authentication and 
    // connection to the Web API
    // 
    // You shouldn't have to modify any of the code...
    private static String spotifyEndpointToJson(String endpoint, String params) {
        params = params.replace(' ', '+');

        try {
            String fullURL = endpoint;
            if (params.isEmpty() == false) {
                fullURL += "?" + params;
            }

            URL requestURL = new URL(fullURL);

            HttpURLConnection connection = (HttpURLConnection) requestURL.openConnection();
            String bearerAuth = "Bearer " + getSpotifyAccessToken();
            connection.setRequestProperty("Authorization", bearerAuth);
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String inputLine;
            String jsonOutput = "";
            while ((inputLine = in.readLine()) != null) {
                jsonOutput += inputLine;
            }
            in.close();

            return jsonOutput;
        } catch (Exception e) {
            //e.printStackTrace();
        }

        return "";
    }

    // This implements the Client Credentials Authorization Flows
    // Based on the Spotify API documentation
    // 
    // It retrieves the Access Token based on the client ID and client Secret  
    //
    // You shouldn't have to modify any of this code...          
    private static String getSpotifyAccessToken() {
        try {
            URL requestURL = new URL("https://accounts.spotify.com/api/token");

            HttpURLConnection connection = (HttpURLConnection) requestURL.openConnection();
            String keys = SPOTIFY_CLIENT_ID + ":" + SPOTIFY_CLIENT_SECRET;
            String postData = "grant_type=client_credentials";

            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(keys.getBytes()));

            // Send header parameter
            connection.setRequestProperty("Authorization", basicAuth);

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Send body parameters
            OutputStream os = connection.getOutputStream();
            os.write(postData.getBytes());
            os.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String inputLine;
            String jsonOutput = "";
            while ((inputLine = in.readLine()) != null) {
                jsonOutput += inputLine;
            }
            in.close();

            JsonElement jelement = new JsonParser().parse(jsonOutput);
            JsonObject rootObject = jelement.getAsJsonObject();
            String token = rootObject.get("access_token").getAsString();

            return token;
        } catch (Exception e) {
            System.out.println("Something wrong here... make sure you set your Client ID and Client Secret properly!");
            e.printStackTrace();
        }

        return "";
    }
}
