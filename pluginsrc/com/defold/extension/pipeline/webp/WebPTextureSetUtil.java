package com.defold.extension.pipeline.webp;

import com.dynamo.bob.CompileExceptionError;
import com.dynamo.bob.Project;
import com.dynamo.bob.fs.IResource;
import com.dynamo.bob.pipeline.AtlasUtil;
import com.dynamo.bob.textureset.TextureSetGenerator;
import com.dynamo.bob.textureset.TextureSetGenerator.TextureSetResult;
import com.dynamo.bob.util.TextureUtil;
import com.dynamo.gamesys.proto.AtlasProto.Atlas;
import com.dynamo.gamesys.proto.AtlasProto.AtlasAnimation;
import com.dynamo.gamesys.proto.AtlasProto.AtlasImage;
import com.dynamo.gamesys.proto.Tile.Playback;
import com.dynamo.gamesys.proto.Tile.SpriteTrimmingMode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.commons.io.FilenameUtils;

public final class WebPTextureSetUtil {
    private static final String WEBP_EXT = "webp";

    private static final class ExpandedImage {
        final List<BufferedImage> images = new ArrayList<BufferedImage>();
        final List<AtlasImage> atlasImages = new ArrayList<AtlasImage>();
        final List<String> paths = new ArrayList<String>();
        final List<String> ids = new ArrayList<String>();
        WebPFrames.Decoded decoded;

        boolean isAnimated() {
            return decoded != null && decoded.isAnimated();
        }

        int fps() {
            return decoded != null ? decoded.getAverageFps() : 0;
        }
    }

    private WebPTextureSetUtil() {
    }

    public static boolean isWebPPath(String path) {
        return WEBP_EXT.equalsIgnoreCase(FilenameUtils.getExtension(path));
    }

    public static TextureSetResult generateWebPTextureSet(IResource resource) throws IOException, CompileExceptionError {
        WebPFrames.Decoded decoded = WebPFrames.read(resource.getContent());
        String baseName = FilenameUtils.getBaseName(resource.getPath());
        ExpandedImage expanded = expandDecodedWebP(resource.getPath(), baseName, decoded, null);

        List<AtlasUtil.MappedAnimDesc> animations = new ArrayList<AtlasUtil.MappedAnimDesc>();
        if (decoded.isAnimated()) {
            animations.add(new AtlasUtil.MappedAnimDesc(baseName, expanded.paths, expanded.ids,
                    Playback.PLAYBACK_LOOP_FORWARD, decoded.getAverageFps(), false, false));
        } else {
            animations.add(new AtlasUtil.MappedAnimDesc(baseName, expanded.paths, expanded.ids));
        }

        AtlasUtil.MappedAnimIterator iterator = new AtlasUtil.MappedAnimIterator(animations, expanded.paths);
        return TextureSetGenerator.generate(expanded.images, expanded.atlasImages, expanded.ids, iterator,
                0, 0, 0, true, false, null, 0, 0);
    }

    public static TextureSetResult generateAtlasTextureSet(Project project, IResource atlasResource, Atlas.Builder builder)
            throws IOException, CompileExceptionError {
        Atlas atlas = builder.build();
        AtlasUtil.validatePatterns(atlas.getRenamePatterns());

        AtlasUtil.PathTransformer transformer = AtlasUtil.createPathTransformer(project, atlas.getRenamePatterns());
        List<AtlasImage> collectedImages = AtlasUtil.collectImages(atlasResource, atlas, transformer);
        Map<String, ExpandedImage> expandedBySourcePath = new HashMap<String, ExpandedImage>();

        List<BufferedImage> images = new ArrayList<BufferedImage>();
        List<AtlasImage> atlasImages = new ArrayList<AtlasImage>();
        List<String> imagePaths = new ArrayList<String>();
        List<String> imageNames = new ArrayList<String>();

        for (AtlasImage image : collectedImages) {
            ExpandedImage expanded = expandAtlasImage(atlasResource, transformer, image, expandedBySourcePath);
            images.addAll(expanded.images);
            atlasImages.addAll(expanded.atlasImages);
            imagePaths.addAll(expanded.paths);
            imageNames.addAll(expanded.ids);
        }

        List<AtlasUtil.MappedAnimDesc> animations = new ArrayList<AtlasUtil.MappedAnimDesc>();
        for (AtlasAnimation animation : atlas.getAnimationsList()) {
            List<String> framePaths = new ArrayList<String>();
            List<String> frameIds = new ArrayList<String>();

            for (AtlasImage image : animation.getImagesList()) {
                ExpandedImage expanded = expandAtlasImage(atlasResource, transformer, image, expandedBySourcePath);
                framePaths.addAll(expanded.paths);
                frameIds.addAll(expanded.ids);
            }

            animations.add(new AtlasUtil.MappedAnimDesc(animation.getId(), framePaths, frameIds,
                    animation.getPlayback(), animation.getFps(), animation.getFlipHorizontal() != 0,
                    animation.getFlipVertical() != 0));
        }

        for (AtlasImage image : atlas.getImagesList()) {
            ExpandedImage expanded = expandAtlasImage(atlasResource, transformer, image, expandedBySourcePath);
            if (expanded.isAnimated()) {
                String animationId = transformer.transform(image.getImage());
                animations.add(new AtlasUtil.MappedAnimDesc(animationId, expanded.paths, expanded.ids,
                        Playback.PLAYBACK_LOOP_FORWARD, expanded.fps(), false, false));
            } else {
                animations.add(new AtlasUtil.MappedAnimDesc(expanded.ids.get(0), expanded.paths, expanded.ids));
            }
        }

        AtlasUtil.MappedAnimIterator iterator = new AtlasUtil.MappedAnimIterator(animations, imagePaths);
        return TextureSetGenerator.generate(images, atlasImages, imageNames, iterator,
                Math.max(0, atlas.getMargin()),
                Math.max(0, atlas.getInnerPadding()),
                Math.max(0, atlas.getExtrudeBorders()),
                true, false, null,
                atlas.getMaxPageWidth(), atlas.getMaxPageHeight());
    }

    private static ExpandedImage expandAtlasImage(IResource atlasResource, AtlasUtil.PathTransformer transformer, AtlasImage image,
            Map<String, ExpandedImage> expandedBySourcePath) throws IOException, CompileExceptionError {
        String sourcePath = image.getImage();
        ExpandedImage cached = expandedBySourcePath.get(sourcePath);
        if (cached != null) {
            return cached;
        }

        IResource imageResource = atlasResource.getResource(sourcePath);
        if (!imageResource.exists()) {
            throw new CompileExceptionError(atlasResource, -1, "Image resource does not exist: " + sourcePath);
        }

        String baseId = transformer.transform(sourcePath);
        ExpandedImage expanded;
        if (isWebPPath(sourcePath)) {
            expanded = expandDecodedWebP(sourcePath, baseId, WebPFrames.read(imageResource.getContent()), image);
        } else {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageResource.getContent()));
            if (bufferedImage == null) {
                throw new CompileExceptionError(imageResource, -1, "Unable to load image " + sourcePath);
            }

            expanded = new ExpandedImage();
            expanded.images.add(bufferedImage);
            expanded.atlasImages.add(image);
            expanded.paths.add(sourcePath);
            expanded.ids.add(baseId);
        }

        expandedBySourcePath.put(sourcePath, expanded);
        return expanded;
    }

    private static ExpandedImage expandDecodedWebP(String sourcePath, String baseId, WebPFrames.Decoded decoded,
            AtlasImage templateImage) {
        ExpandedImage expanded = new ExpandedImage();
        expanded.decoded = decoded;

        int frameCount = decoded.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            String framePath = frameCount > 1 ? sourcePath + "#frame-" + (i + 1) : sourcePath;
            String frameId = frameCount > 1 ? frameId(baseId, i, frameCount) : baseId;

            expanded.images.add(decoded.getFrames().get(i));
            expanded.atlasImages.add(frameAtlasImage(templateImage, framePath));
            expanded.paths.add(framePath);
            expanded.ids.add(frameId);
        }

        return expanded;
    }

    private static AtlasImage frameAtlasImage(AtlasImage templateImage, String framePath) {
        AtlasImage.Builder builder = templateImage != null ? templateImage.toBuilder() : AtlasImage.newBuilder();
        builder.setImage(framePath);
        if (!builder.hasPivotX()) {
            builder.setPivotX(0.5f);
        }
        if (!builder.hasPivotY()) {
            builder.setPivotY(0.5f);
        }
        if (!builder.hasSpriteTrimMode()) {
            builder.setSpriteTrimMode(SpriteTrimmingMode.SPRITE_TRIM_MODE_OFF);
        }
        return builder.build();
    }

    private static String frameId(String baseId, int index, int frameCount) {
        int digits = Math.max(2, Integer.toString(frameCount).length());
        return String.format("%s_%0" + digits + "d", baseId, index + 1);
    }
}
