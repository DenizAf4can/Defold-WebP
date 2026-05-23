package com.defold.extension.pipeline.webp;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class WebPFrames {
    private static final int DEFAULT_DURATION_MS = 100;

    public static final class Decoded {
        private final List<BufferedImage> frames;
        private final List<Integer> durationsMs;
        private final boolean animated;

        private Decoded(List<BufferedImage> frames, List<Integer> durationsMs, boolean animated) {
            this.frames = Collections.unmodifiableList(frames);
            this.durationsMs = Collections.unmodifiableList(durationsMs);
            this.animated = animated;
        }

        public List<BufferedImage> getFrames() {
            return frames;
        }

        public BufferedImage getFirstFrame() {
            return frames.get(0);
        }

        public int getFrameCount() {
            return frames.size();
        }

        public boolean isAnimated() {
            return animated && frames.size() > 1;
        }

        public int getAverageFps() {
            if (durationsMs.isEmpty()) {
                return 10;
            }

            long totalDuration = 0;
            for (Integer duration : durationsMs) {
                totalDuration += Math.max(1, duration);
            }

            int averageDuration = (int) Math.max(1, Math.round((double) totalDuration / durationsMs.size()));
            return Math.max(1, Math.round(1000.0f / averageDuration));
        }

        public BufferedImage toHorizontalSheet() {
            int frameWidth = 0;
            int frameHeight = 0;
            for (BufferedImage frame : frames) {
                frameWidth = Math.max(frameWidth, frame.getWidth());
                frameHeight = Math.max(frameHeight, frame.getHeight());
            }

            BufferedImage sheet = new BufferedImage(frameWidth * frames.size(), frameHeight, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D graphics = sheet.createGraphics();
            graphics.setComposite(AlphaComposite.SrcOver);
            for (int i = 0; i < frames.size(); i++) {
                graphics.drawImage(frames.get(i), i * frameWidth, 0, null);
            }
            graphics.dispose();
            return sheet;
        }
    }

    private WebPFrames() {
    }

    public static Decoded read(byte[] content) throws IOException {
        WebPImageIO.install();

        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IOException("No ImageIO reader found for WebP data.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, false, false);
                int frameCount = Math.max(1, reader.getNumImages(true));
                Object header = fieldValue(reader, "header");
                boolean containsAnimation = booleanField(header, "containsANIM", frameCount > 1);
                int canvasWidth = Math.max(1, intField(header, "width", reader.getWidth(0)));
                int canvasHeight = Math.max(1, intField(header, "height", reader.getHeight(0)));

                if (!containsAnimation && frameCount == 1) {
                    BufferedImage firstFrame = toABGR(reader.read(0));
                    List<BufferedImage> frames = new ArrayList<BufferedImage>();
                    frames.add(firstFrame);
                    List<Integer> durations = new ArrayList<Integer>();
                    durations.add(DEFAULT_DURATION_MS);
                    return new Decoded(frames, durations, false);
                }

                @SuppressWarnings("unchecked")
                List<Object> frameInfos = (List<Object>) fieldValue(reader, "frames");
                BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_4BYTE_ABGR);
                List<BufferedImage> frames = new ArrayList<BufferedImage>(frameCount);
                List<Integer> durations = new ArrayList<Integer>(frameCount);

                for (int i = 0; i < frameCount; i++) {
                    Object frameInfo = frameInfos != null && i < frameInfos.size() ? frameInfos.get(i) : null;
                    Rectangle bounds = rectangleField(frameInfo, "bounds", new Rectangle(0, 0, canvasWidth, canvasHeight));
                    int duration = intField(frameInfo, "duration", DEFAULT_DURATION_MS);
                    boolean blend = booleanField(frameInfo, "blend", true);
                    boolean dispose = booleanField(frameInfo, "dispose", false);
                    BufferedImage frame = toABGR(reader.read(i));

                    if (!blend) {
                        clear(canvas, bounds);
                    }

                    Graphics2D graphics = canvas.createGraphics();
                    graphics.setComposite(blend ? AlphaComposite.SrcOver : AlphaComposite.Src);
                    graphics.drawImage(frame, bounds.x, bounds.y, null);
                    graphics.dispose();

                    frames.add(copy(canvas));
                    durations.add(duration > 0 ? duration : DEFAULT_DURATION_MS);

                    if (dispose) {
                        clear(canvas, bounds);
                    }
                }

                return new Decoded(frames, durations, true);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage toABGR(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
            return image;
        }

        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D graphics = converted.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return converted;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D graphics = copy.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static void clear(BufferedImage image, Rectangle bounds) {
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        graphics.dispose();
    }

    private static Object fieldValue(Object object, String name) {
        if (object == null) {
            return null;
        }

        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int intField(Object object, String name, int defaultValue) {
        Object value = fieldValue(object, name);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static boolean booleanField(Object object, String name, boolean defaultValue) {
        Object value = fieldValue(object, name);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : defaultValue;
    }

    private static Rectangle rectangleField(Object object, String name, Rectangle defaultValue) {
        Object value = fieldValue(object, name);
        return value instanceof Rectangle ? (Rectangle) value : defaultValue;
    }
}

