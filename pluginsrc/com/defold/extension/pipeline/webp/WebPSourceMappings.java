package com.defold.extension.pipeline.webp;

import com.dynamo.bob.Builder;
import com.dynamo.bob.Project;
import com.dynamo.bob.fs.ResourceUtil;
import com.dynamo.bob.util.TextureUtil;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebPSourceMappings {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private WebPSourceMappings() {
    }

    public static void install(Project project) {
        if (INSTALLED.get()) {
            installProjectMappings(project);
            return;
        }

        synchronized (WebPSourceMappings.class) {
            if (INSTALLED.get()) {
                installProjectMappings(project);
                return;
            }

            appendTextureSourceMapping(".webp", ".texturec");
            TextureUtil.registerAtlasFileType(".webp");
            ResourceUtil.registerMapping(".webp", ".texturec");
            installProjectMappings(project);
            INSTALLED.set(true);
        }
    }

    @SuppressWarnings("unchecked")
    private static void installProjectMappings(Project project) {
        try {
            Field extToBuilderField = Project.class.getDeclaredField("extToBuilder");
            extToBuilderField.setAccessible(true);
            Map<String, Class<? extends Builder>> extToBuilder = (Map<String, Class<? extends Builder>>) extToBuilderField
                    .get(project);
            extToBuilder.put(".atlas", WebPAtlasBuilder.class);
            extToBuilder.put(".tilesource", WebPTileSetBuilder.class);
            extToBuilder.put(".tileset", WebPTileSetBuilder.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register Defold WebP builder mappings.", e);
        }
    }

    private static void appendTextureSourceMapping(String sourceExt, String buildExt) {
        try {
            Class<?> protoBuildersClass = Class.forName("com.dynamo.bob.pipeline.ProtoBuilders");
            Field textureSrcExtsField = protoBuildersClass.getDeclaredField("textureSrcExts");
            textureSrcExtsField.setAccessible(true);

            String[][] mappings = (String[][]) textureSrcExtsField.get(null);
            for (String[] mapping : mappings) {
                if (mapping.length >= 2 && sourceExt.equals(mapping[0])) {
                    return;
                }
            }

            String[][] updatedMappings = Arrays.copyOf(mappings, mappings.length + 1);
            updatedMappings[mappings.length] = new String[] { sourceExt, buildExt };
            textureSrcExtsField.set(null, updatedMappings);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register Defold WebP texture source mapping.", e);
        }
    }
}
