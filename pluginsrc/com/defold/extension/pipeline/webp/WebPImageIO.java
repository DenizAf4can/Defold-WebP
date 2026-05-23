package com.defold.extension.pipeline.webp;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.IIOServiceProvider;

public final class WebPImageIO {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final String WEBP_READER_SPI = "com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi";

    private WebPImageIO() {
    }

    public static void install() {
        if (INSTALLED.get()) {
            return;
        }

        synchronized (WebPImageIO.class) {
            if (INSTALLED.get()) {
                return;
            }

            ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader pluginClassLoader = WebPImageIO.class.getClassLoader();

            try {
                Thread.currentThread().setContextClassLoader(pluginClassLoader);
                ImageIO.setUseCache(false);
                ImageIO.scanForPlugins();
                registerProvider(pluginClassLoader, WEBP_READER_SPI);

                if (!hasWebPReader()) {
                    throw new IllegalStateException(
                            "No ImageIO WebP reader is available. Ensure the TwelveMonkeys WebP jars are in webp_import/plugins/share.");
                }

                INSTALLED.set(true);
            } finally {
                Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            }
        }
    }

    public static boolean isInstalled() {
        return INSTALLED.get() || hasWebPReader();
    }

    private static void registerProvider(ClassLoader classLoader, String className) {
        try {
            Class<?> providerClass = Class.forName(className, true, classLoader);
            Object provider = providerClass.getDeclaredConstructor().newInstance();

            if (provider instanceof IIOServiceProvider) {
                IIORegistry.getDefaultInstance().registerServiceProvider((IIOServiceProvider) provider);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register WebP ImageIO provider: " + className, e);
        }
    }

    private static boolean hasWebPReader() {
        Iterator<?> readers = ImageIO.getImageReadersBySuffix("webp");
        return readers.hasNext();
    }
}

