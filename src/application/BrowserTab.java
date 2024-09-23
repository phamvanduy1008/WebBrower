package application;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import org.jsoup.Jsoup;
import javafx.concurrent.Worker;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BrowserTab {
    private Tab thisTab;
    private TextField urlInput;
    private WebView webView;
    private WebEngine webEngine;
    private Button backBtn, forwardBtn, refreshBtn;
    private Label statusLabel;
    private TabPane tabPane;
    private ProgressIndicator loadingIndicator;
    private static List<HistoryEntry> historyList = new ArrayList<>();

    // Inner class to store browsing history
    private class HistoryEntry {
        String url;
        String timestamp;

        HistoryEntry(String url) {
            this.url = url;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    public BrowserTab(TabPane tabPane) {
        this.tabPane = tabPane;
        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        setupLoadingIndicator();
    }

    public Tab initTab() {
        urlInput = new TextField();
        urlInput.setPromptText("Enter URL here...");
        HBox.setHgrow(urlInput, Priority.ALWAYS);

        urlInput.setOnKeyPressed(event -> {
            if (event.getCode().equals(javafx.scene.input.KeyCode.ENTER)) {
                loadURL(urlInput.getText());
            }
        });

        // Load button icons
        Image backIcon = loadImage("/icons/back.png");
        Image forwardIcon = loadImage("/icons/forward.png");
        Image refreshIcon = loadImage("/icons/refresh.png");
        Image goIcon = loadImage("/icons/search.png");
        Image menuIcon = loadImage("/icons/menu.png");

        // Navigation buttons
        backBtn = new Button();
        backBtn.setGraphic(new ImageView(backIcon));
        backBtn.setOnAction(e -> goBack());

        forwardBtn = new Button();
        forwardBtn.setGraphic(new ImageView(forwardIcon));
        forwardBtn.setOnAction(e -> goForward());

        refreshBtn = new Button();
        refreshBtn.setGraphic(new ImageView(refreshIcon));
        refreshBtn.setOnAction(e -> reloadPage());

        Button goBtn = new Button();
        goBtn.setGraphic(new ImageView(goIcon));
        goBtn.setOnAction(e -> loadURL(urlInput.getText()));

        Button menuBtn = new Button();
        menuBtn.setGraphic(new ImageView(menuIcon));
        menuBtn.setOnAction(e -> showPopup(menuBtn));

        // Toolbar setup
        ToolBar toolBar = new ToolBar(backBtn, forwardBtn, refreshBtn, urlInput, goBtn, menuBtn);
        VBox.setVgrow(webView, Priority.ALWAYS);

        statusLabel = new Label("Ready");
        HBox statusBar = new HBox(statusLabel, loadingIndicator);

        VBox content = new VBox(toolBar, webView, statusBar);
        VBox.setVgrow(content, Priority.ALWAYS);
        thisTab = new Tab("New Tab", content);

        // Load default page
        loadURL("http://www.google.com");

        // Event listeners
        webEngine.locationProperty().addListener((observable, oldValue, newValue) -> urlInput.setText(newValue));
        webEngine.setOnStatusChanged(e -> statusLabel.setText(e.getData()));
        webEngine.titleProperty().addListener((observable, oldValue, newValue) -> {
            thisTab.setText(newValue != null ? newValue : "Loading...");
        });

        webEngine.getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                statusLabel.setText("Page loaded successfully");
                loadingIndicator.setVisible(false);
            } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                statusLabel.setText("Failed to load page");
                webEngine.loadContent("<h1>404 Not Found</h1>");
                loadingIndicator.setVisible(false);
            }
        });

        return thisTab;
    }

    private Image loadImage(String path) {
        Image image = new Image(getClass().getResourceAsStream(path));
        if (image.isError()) {
            System.out.println("Error loading image: " + path);
        }
        return image;
    }

    private void setupLoadingIndicator() {
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setPrefSize(20, 20);
    }

    private void showPopup(Button menuBtn) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem getItem = new MenuItem("Analyze (GET)");
        getItem.setOnAction(e -> analyzeHTML(webEngine.getLocation(), true, false));

        MenuItem headItem = new MenuItem("Analyze (HEAD)");
        headItem.setOnAction(e -> analyzeHTML(webEngine.getLocation(), false, true));

        MenuItem historyItem = new MenuItem("History");
        historyItem.setOnAction(e -> showHistory());

        contextMenu.getItems().addAll(getItem, headItem, historyItem);
        contextMenu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void showHistory() {
        VBox historyBox = new VBox();
        List<CheckBox> checkBoxList = new ArrayList<>();

        for (HistoryEntry entry : historyList) {
            HBox historyEntryBox = new HBox();
            CheckBox checkBox = new CheckBox();
            checkBoxList.add(checkBox);

            Hyperlink link = new Hyperlink(entry.url + " (" + entry.timestamp + ")");
            link.setOnAction(e -> openNewTabFromHistory(entry.url));

            historyEntryBox.getChildren().addAll(checkBox, link);
            historyBox.getChildren().add(historyEntryBox);
        }

        Button deleteSelectedBtn = new Button("Delete");
        deleteSelectedBtn.setOnAction(e -> deleteSelectedHistoryItems(checkBoxList, historyBox));

        historyBox.getChildren().add(new Separator());
        historyBox.getChildren().add(deleteSelectedBtn);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("History");
        alert.setHeaderText("Your History:");
        alert.getDialogPane().setContent(historyBox);
        alert.setResizable(true);
        alert.show();
    }

    private void deleteSelectedHistoryItems(List<CheckBox> checkBoxList, VBox historyBox) {
        List<HistoryEntry> itemsToRemove = new ArrayList<>();

        for (int i = 0; i < checkBoxList.size(); i++) {
            if (checkBoxList.get(i).isSelected()) {
                itemsToRemove.add(historyList.get(i));
            }
        }

        if (itemsToRemove.isEmpty()) {
            return; // Nothing selected to delete
        }

        // Show confirmation alert
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation");
        confirmAlert.setHeaderText("Are you sure you want to delete the selected history items?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                historyList.removeAll(itemsToRemove);
                updateHistoryPopup(historyBox); // Update the current popup
            }
        });
    }

    private void updateHistoryPopup(VBox historyBox) {
        historyBox.getChildren().clear(); // Clear existing entries

        List<CheckBox> checkBoxList = new ArrayList<>();
        for (HistoryEntry entry : historyList) {
            HBox historyEntryBox = new HBox();
            CheckBox checkBox = new CheckBox();
            checkBoxList.add(checkBox);

            Hyperlink link = new Hyperlink(entry.url + " (" + entry.timestamp + ")");
            link.setOnAction(e -> openNewTabFromHistory(entry.url));

            historyEntryBox.getChildren().addAll(checkBox, link);
            historyBox.getChildren().add(historyEntryBox);
        }

        Button deleteSelectedBtn = new Button("Delete Selected");
        deleteSelectedBtn.setOnAction(e -> deleteSelectedHistoryItems(checkBoxList, historyBox));

        historyBox.getChildren().add(new Separator());
        historyBox.getChildren().add(deleteSelectedBtn);
    }

    private void analyzeHTML(String url, boolean isGet, boolean isHead) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    if (isHead) {
                        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setRequestMethod("HEAD");
                        connection.connect();

                        int responseCode = connection.getResponseCode();
                        String contentType = connection.getContentType();
                        String contentLength = connection.getHeaderField("Content-Length");

                        String result = String.format("Response Code: %d, Content Type: %s, Content Length: %s",
                                responseCode, contentType, contentLength);

                        Platform.runLater(() -> {
                            statusLabel.setText(result);
                            System.out.println(result);
                        });
                        return null;
                    }

                    Document doc = isGet ? Jsoup.connect(url).get() :
                            Jsoup.connect(url).data("param1", "value1").post();

                    String html = doc.outerHtml();
                    int length = html.length();
                    int pCount = doc.select("p").size();
                    int divCount = doc.select("div").size();
                    int spanCount = doc.select("span").size();
                    int imgCount = doc.select("img").size();

                    String result = String.format("Length: %d, <p>: %d, <div>: %d, <span>: %d, <img>: %d",
                            length, pCount, divCount, spanCount, imgCount);

                    Platform.runLater(() -> {
                        statusLabel.setText(result);
                        System.out.println(result);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to analyze HTML");
                    });
                }
                return null;
            }
        };

        new Thread(task).start();
    }

    private void goBack() {
        WebHistory history = webEngine.getHistory();
        if (history.getCurrentIndex() > 0) {
            history.go(-1);
        }
    }

    private void goForward() {
        WebHistory history = webEngine.getHistory();
        if (history.getCurrentIndex() < history.getEntries().size() - 1) {
            history.go(1);
        }
    }

    private void reloadPage() {
        webEngine.reload();
    }

    public void loadURL(String url) {
        if (url != null && !url.isEmpty()) {
            if (!url.startsWith("http")) {
                url = "http://" + url;
            }
            try {
                webEngine.load(url);
                historyList.add(new HistoryEntry(url));
            } catch (Exception e) {
                statusLabel.setText("Failed to load URL: " + e.getMessage());
            }
        }
    }

    private void openNewTabFromHistory(String url) {
        // Implement logic to open a new tab with the selected URL
        BrowserTab newTab = new BrowserTab(tabPane);
        tabPane.getTabs().add(newTab.initTab());
        newTab.loadURL(url);
    }
}
