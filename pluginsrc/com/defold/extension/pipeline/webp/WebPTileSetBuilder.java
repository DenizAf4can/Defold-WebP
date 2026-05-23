package com.defold.extension.pipeline.webp;

import com.dynamo.bob.BuilderParams;
import com.dynamo.bob.CompileExceptionError;
import com.dynamo.bob.ProtoBuilder;
import com.dynamo.bob.ProtoParams;
import com.dynamo.bob.Task;
import com.dynamo.bob.Task.TaskBuilder;
import com.dynamo.bob.fs.IResource;
import com.dynamo.bob.logging.Logger;
import com.dynamo.bob.pipeline.BuilderUtil;
import com.dynamo.bob.pipeline.Messages;
import com.dynamo.bob.pipeline.TextureGenerator;
import com.dynamo.bob.pipeline.TextureGeneratorException;
import com.dynamo.bob.textureset.TextureSetGenerator.TextureSetResult;
import com.dynamo.bob.tile.TileSetGenerator;
import com.dynamo.bob.util.TextureUtil;
import com.dynamo.gamesys.proto.TextureSetProto.TextureSet;
import com.dynamo.gamesys.proto.Tile.Animation;
import com.dynamo.gamesys.proto.Tile.Playback;
import com.dynamo.gamesys.proto.Tile.TileSet;
import com.dynamo.graphics.proto.Graphics.TextureProfile;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.commons.io.FilenameUtils;

@ProtoParams(srcClass = TileSet.class, messageClass = TileSet.class)
@BuilderParams(name = "WebPTileSet", inExts = { ".tileset", ".tilesource" }, outExt = ".t.texturesetc", isCacheble = true, paramsForSignature = {
        "texture-compression" })
public final class WebPTileSetBuilder extends ProtoBuilder<TileSet.Builder> {
    private static final Logger logger = Logger.getLogger(WebPTileSetBuilder.class.getName());

    @Override
    public Task create(IResource input) throws IOException, CompileExceptionError {
        TileSet.Builder builder = getSrcBuilder(input);
        String imagePath = builder.getImage();
        String collisionPath = builder.getCollision();
        IResource image = this.project.getResource(imagePath);
        IResource collision = this.project.getResource(collisionPath);

        if (image.exists() || collision.exists()) {
            TaskBuilder taskBuilder = Task.newBuilder(this)
                    .setName(params.name())
                    .addInput(input)
                    .addOutput(input.changeExt(params.outExt()));
            String texturePath = String.format("%s__%s_tilesource.%s", FilenameUtils.getPath(input.getPath()),
                    FilenameUtils.getBaseName(input.getPath()), "texturec");
            taskBuilder.addOutput(this.project.getResource(texturePath).output());
            if (image.exists()) {
                taskBuilder.addInput(image);
            }
            if (collision.exists()) {
                taskBuilder.addInput(collision);
            }

            TextureUtil.addTextureProfileInput(taskBuilder, project);
            return taskBuilder.build();
        }

        if (!imagePath.isEmpty()) {
            BuilderUtil.checkResource(this.project, input, "image", imagePath);
        } else if (!collisionPath.isEmpty()) {
            BuilderUtil.checkResource(this.project, input, "collision", collisionPath);
        } else {
            throw new CompileExceptionError(input, 0, Messages.TileSetBuilder_MISSING_IMAGE_AND_COLLISION);
        }
        return null;
    }

    @Override
    public void build(Task task) throws CompileExceptionError, IOException {
        TextureProfile textureProfile = TextureUtil.getTextureProfileByPath(task.lastInput(), task.firstInput().getPath());
        logger.fine("Compiling %s using profile %s", task.firstInput().getPath(),
                textureProfile != null ? textureProfile.getName() : "<none>");

        TileSet.Builder builder = getSrcBuilder(task.firstInput());
        String imagePath = builder.getImage();
        String collisionPath = builder.getCollision();
        IResource imageResource = this.project.getResource(imagePath);
        IResource collisionResource = this.project.getResource(collisionPath);

        BufferedImage image = null;
        if (imageResource.exists()) {
            if (WebPTextureSetUtil.isWebPPath(imagePath)) {
                WebPFrames.Decoded decoded = WebPFrames.read(imageResource.getContent());
                image = decoded.toHorizontalSheet();
                if (decoded.isAnimated() && builder.getAnimationsCount() == 0) {
                    builder.addAnimations(Animation.newBuilder()
                            .setId(FilenameUtils.getBaseName(imagePath))
                            .setStartTile(1)
                            .setEndTile(decoded.getFrameCount())
                            .setPlayback(Playback.PLAYBACK_LOOP_FORWARD)
                            .setFps(decoded.getAverageFps())
                            .setFlipHorizontal(0)
                            .setFlipVertical(0));
                }
            } else {
                image = ImageIO.read(new ByteArrayInputStream(imageResource.getContent()));
            }
        }

        TileSet tileSet = builder.build();
        if (image != null && (image.getWidth() < tileSet.getTileWidth() || image.getHeight() < tileSet.getTileHeight())) {
            throw new CompileExceptionError(task.firstInput(), -1, String.format(
                    "the image dimensions (%dx%d) are smaller than the tile dimensions (%dx%d)", image.getWidth(),
                    image.getHeight(), tileSet.getTileWidth(), tileSet.getTileHeight()));
        }

        BufferedImage collisionImage = null;
        if (collisionResource.exists()) {
            BufferedImage originalCollisionImage = ImageIO.read(new ByteArrayInputStream(collisionResource.getContent()));
            collisionImage = new BufferedImage(originalCollisionImage.getWidth(), originalCollisionImage.getHeight(),
                    BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D graphics = collisionImage.createGraphics();
            graphics.drawImage(originalCollisionImage, 0, 0, null);
            graphics.dispose();
        }

        if (image != null && collisionImage != null
                && (image.getWidth() != collisionImage.getWidth() || image.getHeight() != collisionImage.getHeight())) {
            throw new CompileExceptionError(task.input(0), -1, String.format(
                    "the image and collision image has different dimensions: (%dx%d) vs (%dx%d)", image.getWidth(),
                    image.getHeight(), collisionImage.getWidth(), collisionImage.getHeight()));
        }
        if (collisionImage != null && !collisionImage.getColorModel().hasAlpha()) {
            throw new CompileExceptionError(task.input(0), -1, "the collision image does not have an alpha channel");
        }

        TextureSetResult result = TileSetGenerator.generate(tileSet, image, collisionImage);
        TextureSet.Builder textureSetBuilder = result.builder;

        int buildDirLen = project.getBuildDirectory().length();
        String texturePath = task.output(1).getPath().substring(buildDirLen);
        TextureSet textureSet = textureSetBuilder.setTexture(texturePath).build();

        try {
            boolean compress = project.option("texture-compression", "false").equals("true");
            TextureGenerator.GenerateResult generateResult = TextureGenerator.generate(result.images.get(0), textureProfile, compress);
            task.output(0).setContent(textureSet.toByteArray());
            TextureUtil.writeGenerateResultToResource(generateResult, task.output(1));
        } catch (TextureGeneratorException e) {
            throw new CompileExceptionError(task.input(0), -1, e.getMessage(), e);
        }
    }
}

