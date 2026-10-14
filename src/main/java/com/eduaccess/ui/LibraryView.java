package com.eduaccess.ui;

import com.eduaccess.model.DigitalResource;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ResourceTab;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.service.UsbService;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;

/** Screen 8: the K-12 and College tabs, offline cache toggles, and the multi-file USB export panel. */
final class LibraryView {

    private final AppContext ctx;
    private final AppShell shell;
    private ResourceTab tab = ResourceTab.K12_MODULE_HUB;
    private final TextField search = new TextField();
    private final ComboBox<EducationLevel> level = new ComboBox<>();
    private final VBox list = new VBox(10);
    private final Set<Long> selected = new HashSet<>();
    private final ComboBox<Path> usbTarget = new ComboBox<>();
    private final Label noDrive = Ui.muted("No USB drive detected. Plug one in and refresh, or choose a folder.");
    private final Label offlineSummary = Ui.muted("");
    private final Label usbSummary = Ui.muted("");
    private final Button copy = new Button("Copy selected to USB");

    LibraryView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        Label title = Ui.heading("Digital Library");
        Label subtitle = Ui.muted("Free DepEd learning modules and open college e-books. Download once, read offline.");

        ToggleGroup tabs = new ToggleGroup();
        ToggleButton k12 = tabButton("K-12 Module Hub", ResourceTab.K12_MODULE_HUB, tabs);
        ToggleButton college = tabButton("College & Higher Ed Repository", ResourceTab.COLLEGE_REPOSITORY, tabs);
        k12.setSelected(true);
        tabs.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) {
                was.setSelected(true);
            }
        });
        HBox tabRow = Ui.row(10, k12, college);
        if (ctx.session.require().role() == Role.ADMIN) {
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            Button add = new Button("+ Add resource");
            add.getStyleClass().add("button-teal");
            add.setOnAction(event -> AdminForms.addDigitalResource(ctx, this::refresh));
            tabRow.getChildren().addAll(gap, add);
        }

        search.setPromptText("Search e-books, journals, modules...");
        search.textProperty().addListener((obs, was, now) -> refresh());
        HBox.setHgrow(search, Priority.ALWAYS);
        configureLevel();
        HBox filters = Ui.row(10, search, level);

        VBox main = new VBox(12, tabRow, filters, list);
        HBox.setHgrow(main, Priority.ALWAYS);
        VBox side = sidePanel();
        side.setPrefWidth(300);
        side.setMinWidth(300);

        reloadTargets();
        refresh();
        return new VBox(14, new VBox(4, title, subtitle), new HBox(20, main, side));
    }

    private ToggleButton tabButton(String text, ResourceTab target, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("toggle-card");
        button.setToggleGroup(group);
        button.selectedProperty().addListener((obs, was, now) -> {
            if (now) {
                tab = target;
                selected.clear();
                refresh();
            }
        });
        return button;
    }

    private void configureLevel() {
        level.getItems().add(null);
        level.getItems().addAll(EducationLevel.values());
        level.setConverter(new StringConverter<>() {
            @Override
            public String toString(EducationLevel value) {
                return value == null ? "All levels" : value.dbValue();
            }

            @Override
            public EducationLevel fromString(String text) {
                return null;
            }
        });
        level.valueProperty().addListener((obs, was, now) -> refresh());
    }

    private void refresh() {
        List<DigitalResource> resources = ctx.library.browse(tab, level.getValue(), search.getText());
        list.getChildren().clear();
        if (resources.isEmpty()) {
            list.getChildren().add(Ui.muted("No resources match your filters."));
        }
        for (DigitalResource resource : resources) {
            list.getChildren().add(row(resource));
        }
        updateSummaries();
    }

    private HBox row(DigitalResource resource) {
        User user = ctx.session.require();
        CheckBox pick = new CheckBox();
        pick.setDisable(!resource.cachedOffline());
        pick.setSelected(selected.contains(resource.id()));
        pick.selectedProperty().addListener((obs, was, now) -> {
            if (now) {
                selected.add(resource.id());
            } else {
                selected.remove(resource.id());
            }
            updateSummaries();
        });

        Label title = new Label(resource.title());
        title.getStyleClass().add("item-title");
        title.setWrapText(true);
        Label detail = Ui.muted(resource.subject() + "  ·  " + resource.level().dbValue() + "  ·  "
                + Ui.sizeText(resource.fileSizeMb()) + "  ·  Source: " + resource.source());
        Label badge = resource.cachedOffline() ? Ui.badge("Cached offline", "cached") : Ui.badge("Online only", "online");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox actions = new HBox(10);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        if (resource.cachedOffline()) {
            Button open = new Button("Open PDF");
            open.getStyleClass().add("button-primary");
            open.setOnAction(event -> openPdf(resource));
            Button remove = new Button("Remove from offline");
            remove.getStyleClass().add("button-secondary");
            remove.setOnAction(event -> Ui.run(() -> {
                ctx.library.setCached(resource.id(), false);
                selected.remove(resource.id());
                refresh();
            }));
            actions.getChildren().addAll(open, remove);
        } else {
            Button download = new Button("Download");
            download.getStyleClass().add("button-secondary");
            download.setOnAction(event -> Ui.run(() -> {
                ctx.library.download(user, resource.id());
                refresh();
            }));
            actions.getChildren().add(download);
        }

        HBox row = Ui.row(14, pick, Ui.documentArt(resource.tab() == ResourceTab.COLLEGE_REPOSITORY, 56, 64),
                new VBox(4, title, detail, badge), gap, actions);
        row.getStyleClass().add("card");
        return row;
    }

    private void openPdf(DigitalResource resource) {
        Path file = Path.of(resource.filePath());
        if (!Files.exists(file)) {
            Ui.error("This file is missing from the storage folder: " + resource.filePath());
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.toFile());
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Ui.error("No PDF viewer could open this file. It is stored at: " + file.toAbsolutePath());
    }

    private VBox sidePanel() {
        usbTarget.setMaxWidth(Double.MAX_VALUE);
        usbTarget.setConverter(new StringConverter<>() {
            @Override
            public String toString(Path value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Path fromString(String text) {
                return Path.of(text);
            }
        });
        usbTarget.valueProperty().addListener((obs, was, now) -> updateSummaries());

        Button refreshDrives = new Button("Refresh drives");
        refreshDrives.getStyleClass().add("button-secondary");
        refreshDrives.setOnAction(event -> reloadTargets());

        Button choose = new Button("Choose folder...");
        choose.getStyleClass().add("button-secondary");
        choose.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose where to copy the files");
            File folder = chooser.showDialog(choose.getScene().getWindow());
            if (folder != null) {
                usbTarget.getItems().remove(folder.toPath());
                usbTarget.getItems().add(0, folder.toPath());
                usbTarget.setValue(folder.toPath());
            }
        });

        copy.getStyleClass().add("button-primary");
        copy.setMaxWidth(Double.MAX_VALUE);
        copy.setOnAction(event -> copySelected());

        Label offlineTitle = Ui.section("Offline storage");
        Label usbTitle = Ui.section("Copy to USB drive");
        return Ui.card(offlineTitle, offlineSummary, new Separator(), usbTitle, usbTarget, noDrive,
                Ui.row(8, refreshDrives, choose), usbSummary, copy,
                Ui.muted("Copies go to an \"EduAccess Resources\" folder, so your own files are never overwritten."));
    }

    private void reloadTargets() {
        List<Path> targets = ctx.usb.detectTargets();
        usbTarget.getItems().setAll(targets);
        if (!targets.isEmpty()) {
            usbTarget.setValue(targets.get(0));
        }
        noDrive.setVisible(targets.isEmpty());
        noDrive.setManaged(targets.isEmpty());
        updateSummaries();
    }

    private void updateSummaries() {
        List<DigitalResource> cached = ctx.library.cached();
        double cachedMb = cached.stream().mapToDouble(DigitalResource::fileSizeMb).sum();
        offlineSummary.setText(cached.size() + " file(s), " + Ui.sizeText(cachedMb)
                + " saved on this computer. They open without internet.");

        List<DigitalResource> chosen = cached.stream().filter(r -> selected.contains(r.id())).toList();
        double chosenMb = chosen.stream().mapToDouble(DigitalResource::fileSizeMb).sum();
        usbSummary.setText(chosen.size() + " file(s) selected, " + Ui.sizeText(chosenMb) + " in total.");
        copy.setDisable(chosen.isEmpty() || usbTarget.getValue() == null);
    }

    private void copySelected() {
        Path target = usbTarget.getValue();
        List<Path> files = ctx.library.cached().stream()
                .filter(r -> selected.contains(r.id()))
                .map(r -> Path.of(r.filePath()))
                .toList();
        Ui.run(() -> {
            UsbService.CopyResult result = ctx.usb.copyTo(files, target);
            StringBuilder message = new StringBuilder("Copied " + result.copied().size() + " file(s) to "
                    + result.destination() + ".");
            if (!result.failed().isEmpty()) {
                message.append("\n\nCould not copy:\n").append(String.join("\n", result.failed()));
            }
            Ui.info(message.toString());
        });
    }
}
