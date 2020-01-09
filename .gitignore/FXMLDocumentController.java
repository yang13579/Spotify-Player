/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package spotifyplayer;

import com.sun.javafx.collections.ObservableListWrapper;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import javafx.util.Duration;
import javax.imageio.ImageIO;

/**
 *
 * @author bergeron
 */
public class FXMLDocumentController implements Initializable {

    @FXML
    ImageView albumCoverImageView;

    @FXML
    TableView tracksTableView;

    @FXML
    Slider trackSlider;

    @FXML
    Label artistNameLabel;

    @FXML
    Label albumNameLabel;

    @FXML
    Label timeLabel;

    @FXML
    TextField artistTextField;

    @FXML
    ProgressIndicator progress;

    @FXML
    Button previousAlbumButton;

    @FXML
    Button nextAlbumButton;

    @FXML
    Button buttomPlayButton;

    Button lastPlayButtonPressed;

    // Other Fields...
    ScheduledExecutorService sliderExecutor = null;
    ScheduledExecutorService searchExecutor = null;
    MediaPlayer mediaPlayer = null;

    ArrayList<Album> albums = null;
    int currentAlbumIndex = 0;

    private void playPauseTrackPreview(Button source, String trackPreviewUrl) {
        try {
            if (source.getText().equals("Play")) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                }

                source.setText("Stop");
                buttomPlayButton.setText("Stop");
                trackSlider.setDisable(false);
                trackSlider.setValue(0.0);

                // Start playing music
                Media music = new Media(trackPreviewUrl);
                mediaPlayer = new MediaPlayer(music);
                mediaPlayer.play();

                // This runnable object will be called
                // when the track is finished or stopped
                Runnable stopTrackRunnable = new Runnable() {
                    @Override
                    public void run() {
                        source.setText("Play");
                        //buttomPlayButton.setText("Play");
                        if (sliderExecutor != null) {
                            sliderExecutor.shutdownNow();
                        }
                    }
                };
                mediaPlayer.setOnEndOfMedia(stopTrackRunnable);
                mediaPlayer.setOnStopped(stopTrackRunnable);

                // Schedule the slider to move right every second
                sliderExecutor = Executors.newSingleThreadScheduledExecutor();
                sliderExecutor.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        // We can't update the GUI elements on a separate thread... 
                        // Let's call Platform.runLater to do it in main thread!!
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                // Move slider
                                trackSlider.setValue(trackSlider.getValue() + 0.001);
                            }
                        });
                    }
                }, 1, 1, TimeUnit.MILLISECONDS);
                /*
                                trackSlider.setValue(trackSlider.getValue() + 1.0);
                            }
                        });
                    }
                }, 1, 1, TimeUnit.SECONDS);
                 */
            } else {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    buttomPlayButton.setText("Play");
                }
            }
        } catch (Exception e) {
            System.err.println("error with slider executor... this should not happen!");
        }
    }

    private void displayAlbum(int albumNumber) {
        // TODO - Display all the informations about the album
        //
        //        Artist Name 
        //        Album Name
        //        Album Cover Image
        //        Enable next/previous album buttons, if there is more than one album

        // Display Tracks for the album passed as parameter
        if (albumNumber >= 0 && albumNumber < albums.size()) {
            currentAlbumIndex = albumNumber;
            Album album = albums.get(albumNumber);

            artistNameLabel.setText(album.getArtistName());
            albumNameLabel.setText(album.getAlbumName());
            albumCoverImageView.setImage(new Image(album.getImageURL()));

            if (albums.size() == 1) {
                previousAlbumButton.setDisable(true);
                nextAlbumButton.setDisable(true);
            } else {
                previousAlbumButton.setDisable(false);
                nextAlbumButton.setDisable(false);
            }

            // Set tracks
            ArrayList<TrackForTableView> tracks = new ArrayList<>();
            for (int i = 0; i < album.getTracks().size(); ++i) {
                TrackForTableView trackForTable = new TrackForTableView();
                Track track = album.getTracks().get(i);
                trackForTable.setTrackNumber(track.getNumber());
                trackForTable.setTrackTitle(track.getTitle());
                trackForTable.setTrackPreviewUrl(track.getUrl());
                tracks.add(trackForTable);
            }
            tracksTableView.setItems(new ObservableListWrapper(tracks));

            trackSlider.setDisable(true);
            trackSlider.setValue(0.0);
        }
    }

    @FXML
    private void searchForArtistEvent(Event event) {
        progress.setVisible(true);

        searchExecutor.submit(new Task<Void>() {
            @Override
            protected Void call() throws Exception {

                try {
                    searchAlbumsFromArtist(artistTextField.getText());

                    if (albums == null || albums.size() == 0) {
                        cancel();
                    }

                    return null;
                } catch (Exception e) {
                    cancel();
                }

                return null;
            }

            @Override
            protected void succeeded() {

                displayAlbum(0);
                progress.setVisible(false);
            }

            @Override
            protected void cancelled() {
                artistNameLabel.setText("Error!!");
                albumNameLabel.setText("No album found!");
                progress.setVisible(false);
            }
        });

    }

    private void searchAlbumsFromArtist(String artistName) {
        // TODO - Make sure this is not blocking the UI
        currentAlbumIndex = 0;
        String artistId = SpotifyController.getArtistId(artistName);
        albums = SpotifyController.getAlbumDataFromArtist(artistId);
    }

    public void shutdown() {
        if (sliderExecutor != null) {
            sliderExecutor.shutdown();
        }
        if (searchExecutor != null) {
            searchExecutor.shutdown();
        }
        Platform.exit();
    }

    @FXML
    private void previousAlbumButtonAction(ActionEvent event) {
        if (currentAlbumIndex < 0) {
            currentAlbumIndex += albums.size();
        } else {
            currentAlbumIndex--;
        }
        displayAlbum(currentAlbumIndex);
    }

    @FXML
    private void nextAlbumButtonAction(ActionEvent event) {
        if (currentAlbumIndex > albums.size()) {
            currentAlbumIndex -= albums.size();
        } else {
            currentAlbumIndex++;
        }
        displayAlbum(currentAlbumIndex);
    }

    @FXML
    private void playButtonClick(ActionEvent event) {
        if (lastPlayButtonPressed != null) {
            lastPlayButtonPressed.fire();

            /*
            if (buttomPlayButton.getText().equals("Stop")) {

                buttomPlayButton.setText("Play");
                // lastPlayButtonPressed.setText("Play");
            } else {
                buttomPlayButton.setText("Stop");
                // lastPlayButtonPressed.setText("Stop");
            }
            */
        }

    }

    @FXML
    private void saveButtonAction(ActionEvent event) {
        BufferedImage image = SwingFXUtils.fromFXImage(albumCoverImageView.getImage(), null);
        FileChooser fileChooser = new FileChooser();
        //File file = fileChooser.showSaveDialog(null);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Images", "*.*"),
                new FileChooser.ExtensionFilter("JPG", "*.jpg"),
                new FileChooser.ExtensionFilter("PNG", "*.png")
        );
        fileChooser.setTitle("Save File");
        File file = fileChooser.showSaveDialog(nextAlbumButton.getScene().getWindow());
        if (file != null) {
            try {
                ImageIO.write(image, "png", file);
            } catch (IOException ex) {
                artistNameLabel.setText("Error!!");
                albumNameLabel.setText("unable to save the image");
            }
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
        buttomPlayButton.setDisable(true);
        
        trackSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue,
                    Number oldValue, Number newValue) {
                if (newValue == null) {
                    return;
                }
                if (newValue.intValue() < 10){
                    timeLabel.setText("00:0" + newValue.intValue() + " / 00:30");
                }
                else{
                    timeLabel.setText("00:" + newValue.intValue() + " / 00:30");
                }
            }
        });

        // Setup Table View
        TableColumn<TrackForTableView, Number> trackNumberColumn = new TableColumn("#");
        trackNumberColumn.setCellValueFactory(new PropertyValueFactory("trackNumber"));

        TableColumn trackTitleColumn = new TableColumn("Title");
        trackTitleColumn.setCellValueFactory(new PropertyValueFactory("trackTitle"));
        trackTitleColumn.setPrefWidth(250);

        TableColumn playColumn = new TableColumn("Preview");
        playColumn.setCellValueFactory(new PropertyValueFactory("trackPreviewUrl"));
        Callback<TableColumn<TrackForTableView, String>, TableCell<TrackForTableView, String>> cellFactory = new Callback<TableColumn<TrackForTableView, String>, TableCell<TrackForTableView, String>>() {
            @Override
            public TableCell<TrackForTableView, String> call(TableColumn<TrackForTableView, String> param) {
                final TableCell<TrackForTableView, String> cell = new TableCell<TrackForTableView, String>() {
                    final Button playButton = new Button("Play");

                    @Override
                    public void updateItem(String item, boolean empty) {
                        if (item != null && item.equals("") == false) {
                            playButton.setOnAction(event -> {
                                buttomPlayButton.setDisable(false);
                                lastPlayButtonPressed = playButton;
                                playPauseTrackPreview(playButton, item);
                                
                                

                            });

                            setGraphic(playButton);
                        } else {
                            setGraphic(null);
                        }

                        setText(null);
                    }
                };

                return cell;
            }
        };
        playColumn.setCellFactory(cellFactory);
        tracksTableView.getColumns().setAll(trackNumberColumn, trackTitleColumn, playColumn);

        // When slider is released, we must seek in the song
        trackSlider.setOnMouseReleased(new EventHandler() {
            @Override
            public void handle(Event event) {
                if (mediaPlayer != null) {
                    mediaPlayer.seek(Duration.seconds(trackSlider.getValue()));
                }
            }
        });

        searchExecutor = Executors.newSingleThreadScheduledExecutor();

        
        searchAlbumsFromArtist("Pink Floyd");
        displayAlbum(0);
    }
}
