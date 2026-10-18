package com.eduaccess.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Line icons drawn on a 24 x 24 grid as SVG paths. Each icon is stroked, not filled, and its colour comes from
 * the stylesheet through the {@code .icon} class ({@code -fx-stroke}), so it follows hover and selected states.
 */
enum Icons {
    HOME("M3 10.5 L12 3 L21 10.5 M5.5 8.6 V20.5 H18.5 V8.6 M9.8 20.5 V14.5 H14.2 V20.5"),
    BOOK("M12 6.5 C10 4.8 7 4.5 3.5 5 V19 C7 18.5 10 18.8 12 20.5 C14 18.8 17 18.5 20.5 19 V5 C17 4.5 14 4.8 12 6.5 Z M12 6.5 V20.5"),
    BOX("M3.5 7.5 L12 3.5 L20.5 7.5 V16.5 L12 20.5 L3.5 16.5 Z M3.5 7.5 L12 11.5 L20.5 7.5 M12 11.5 V20.5"),
    SWAP("M4 8 H19 M15 4 L19 8 L15 12 M20 16 H5 M9 12 L5 16 L9 20"),
    HEART("M12 20 C5 15.2 3 11.6 3 8.6 C3 6 5 4 7.5 4 C9.4 4 11 5 12 6.6 C13 5 14.6 4 16.5 4 C19 4 21 6 21 8.6 C21 11.6 19 15.2 12 20 Z"),
    LIST("M9 6.5 H20 M9 12 H20 M9 17.5 H20 M4.5 6.5 H5 M4.5 12 H5 M4.5 17.5 H5"),
    TABLET("M6.5 3 H17.5 C18.3 3 19 3.7 19 4.5 V19.5 C19 20.3 18.3 21 17.5 21 H6.5 C5.7 21 5 20.3 5 19.5 V4.5 C5 3.7 5.7 3 6.5 3 Z M10.5 17.5 H13.5 M8.5 7.5 H15.5 M8.5 11 H13"),
    SPARKLE("M12 3 L13.7 10.3 L21 12 L13.7 13.7 L12 21 L10.3 13.7 L3 12 L10.3 10.3 Z M19 3 V6.5 M17.2 4.8 H20.8"),
    CLIPBOARD("M9 4 H15 V6.5 H9 Z M9 5 H6 V21 H18 V5 H15 M9 13 L11 15 L15.2 10.8"),
    CHART("M4 20 H20 M7 20 V11 M12 20 V5 M17 20 V14"),
    SEARCH("M10.5 17.5 C14.4 17.5 17.5 14.4 17.5 10.5 C17.5 6.6 14.4 3.5 10.5 3.5 C6.6 3.5 3.5 6.6 3.5 10.5 C3.5 14.4 6.6 17.5 10.5 17.5 Z M15.5 15.5 L20.5 20.5"),
    BELL("M6 16.5 V11 C6 7.7 8.7 5 12 5 C15.3 5 18 7.7 18 11 V16.5 L19.5 18 H4.5 Z M10 20.5 H14 M12 3 V5"),
    LOGOUT("M14 4 H19 V20 H14 M10 8 L6 12 L10 16 M6 12 H16"),
    CALCULATOR("M6.5 3 H17.5 V21 H6.5 Z M9 6 H15 V9 H9 Z M9 12.5 H9.5 M12 12.5 H12.5 M15 12.5 H15.5 M9 15.5 H9.5 M12 15.5 H12.5 M15 15.5 H15.5 M9 18.5 H9.5 M12 18.5 H12.5 M15 18.5 H15.5"),
    LAPTOP("M5 5.5 H19 V15.5 H5 Z M3 18.5 H21"),
    FLASK("M9.5 3 H14.5 M10.5 3 V9 L5 19 C4.6 19.8 5.1 21 6.1 21 H17.9 C18.9 21 19.4 19.8 19 19 L13.5 9 V3 M7.5 15 H16.5"),
    CALENDAR("M4.5 6 H19.5 V20.5 H4.5 Z M4.5 10 H19.5 M8.5 3.5 V7.5 M15.5 3.5 V7.5"),
    CLOCK("M12 21 C17 21 21 17 21 12 C21 7 17 3 12 3 C7 3 3 7 3 12 C3 17 7 21 12 21 Z M12 7 V12 L15 14"),
    PIN("M12 21 C12 21 5 14.5 5 9.5 C5 5.6 8.1 3 12 3 C15.9 3 19 5.6 19 9.5 C19 14.5 12 21 12 21 Z M12 12 C13.4 12 14.5 10.9 14.5 9.5 C14.5 8.1 13.4 7 12 7 C10.6 7 9.5 8.1 9.5 9.5 C9.5 10.9 10.6 12 12 12 Z"),
    TAG("M3.5 12 L12 3.5 H20.5 V12 L12 20.5 Z M16 8 H16.5"),
    UPLOAD("M12 15.5 V4 M7.5 8.5 L12 4 L16.5 8.5 M4 15 V20 H20 V15"),
    PLUS("M12 5 V19 M5 12 H19"),
    CHECK("M5 12.5 L10 17.5 L19 7"),
    ALERT("M12 3.5 L21.5 20 H2.5 Z M12 10 V14 M12 17 V17.5"),
    INFO("M12 21 C17 21 21 17 21 12 C21 7 17 3 12 3 C7 3 3 7 3 12 C3 17 7 21 12 21 Z M12 11 V16.5 M12 7.5 V8"),
    ARROW_LEFT("M19 12 H5 M11 6 L5 12 L11 18"),
    USERS("M9 11 C11 11 12.5 9.4 12.5 7.5 C12.5 5.6 11 4 9 4 C7 4 5.5 5.6 5.5 7.5 C5.5 9.4 7 11 9 11 Z M3 20 C3 16.7 5.7 14 9 14 C12.3 14 15 16.7 15 20 M15.5 4.3 C17.2 4.8 18.4 6 18.4 7.6 C18.4 9.2 17.2 10.5 15.5 10.9 M18 14.3 C20 15 21 17.2 21 20"),
    LAYERS("M12 3.5 L21 8 L12 12.5 L3 8 Z M3 12.5 L12 17 L21 12.5 M3 16.5 L12 21 L21 16.5");

    private final String path;

    Icons(String path) {
        this.path = path;
    }

    /** The icon at {@code size} pixels. Styled by {@code .icon} and any rule that targets a parent. */
    Node node(double size) {
        double scale = size / 24.0;
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.getStyleClass().add("icon");
        svg.setFill(null);
        svg.setStrokeWidth(1.75 / scale);
        svg.setStrokeLineCap(StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(StrokeLineJoin.ROUND);
        // A fixed 24 x 24 frame keeps every icon the same size and centred, whatever its own bounds are.
        Rectangle frame = new Rectangle(24, 24, null);
        Group drawing = new Group(frame, svg);
        drawing.setScaleX(scale);
        drawing.setScaleY(scale);
        return new Group(drawing);
    }
}
