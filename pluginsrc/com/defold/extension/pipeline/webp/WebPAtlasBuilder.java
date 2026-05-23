package com.defold.extension.pipeline.webp;

import com.dynamo.bob.BuilderParams;
import com.dynamo.bob.CompileExceptionError;
import com.dynamo.bob.ProtoBuilder;
import com.dynamo.bob.ProtoParams;
import com.dynamo.bob.Task;
import com.dynamo.bob.Task.TaskBuilder;
import com.dynamo.bob.fs.IResource;
import com.dynamo.bob.logging.Logger;
import com.dynamo.bob.pipeline.TextureGenerator;
import com.dynamo.bob.pipeline.TextureGeneratorException;
import com.dynamo.bob.textureset.TextureSetGenerator.TextureSetResult;
import com.dynamo.bob.util.TextureUtil;
import com.dynamo.gamesys.proto.AtlasProto.Atlas;
import com.dynamo.gamesys.proto.AtlasProto.AtlasImage;
import com.dynamo.gamesys.proto.TextureSetProto.TextureSet;
import com.dynamo.graphics.proto.Graphics.TextureImage;
import com.dynamo.graphics.proto.Graphics.TextureProfile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

@ProtoParams(srcClass = Atlas.class, messageClass = TextureSet.class)
@BuilderParams(name = "WebPAtlas", inExts = { ".atlas" }, outExt = ".a.texturesetc", isCacheble = true, paramsForSignature = {
        "texture-compression" })
public final class WebPAtlasBuilder extends ProtoBuilder<Atlas.Builder> {
    private static final Logger logger = Logger.getLogger(WebPAtlasBuilder.class.getName());

    private static TextureImage.Type getTextureType(Atlas.Builder builder) {
        return builder.getMaxPageWidth() > 0 && builder.getMaxPageHeight() > 0 ? TextureImage.Type.TYPE_2D_ARRAY
                : TextureImage.Type.TYPE_2D;
    }

    private static int getPageCount(List<BufferedImage> images, TextureImage.Type textureType) {
        return textureType == TextureImage.Type.TYPE_2D ? 0 : images.size();
    }

    @Override
    public Task create(IResource input) throws IOException, CompileExceptionError {
        Atlas.Builder builder = getSrcBuilder(input);
        Atlas atlas = builder.build();

        TaskBuilder taskBuilder = Task.newBuilder(this)
                .setName(params.name())
                .addInput(input)
                .addOutput(input.changeExt(params.outExt()))
                .addOutput(input.changeExt(".texturec"));

        com.dynamo.bob.pipeline.AtlasUtil.PathTransformer transformer = com.dynamo.bob.pipeline.AtlasUtil
                .createPathTransformer(project, atlas.getRenamePatterns());
        for (AtlasImage image : com.dynamo.bob.pipeline.AtlasUtil.collectImages(input, atlas, transformer)) {
            taskBuilder.addInput(input.getResource(image.getImage()));
        }

        TextureUtil.addTextureProfileInput(taskBuilder, project);
        return taskBuilder.build();
    }

    @Override
    public void build(Task task) throws CompileExceptionError, IOException {
        Atlas.Builder builder = getSrcBuilder(task.firstInput());
        TextureSetResult result = WebPTextureSetUtil.generateAtlasTextureSet(this.project, task.firstInput(), builder);
        TextureImage.Type textureImageType = getTextureType(builder);

        int buildDirLen = project.getBuildDirectory().length();
        String texturePath = task.output(1).getPath().substring(buildDirLen);
        TextureSet textureSet = result.builder.setPageCount(getPageCount(result.images, textureImageType))
                .setTexture(texturePath)
                .build();

        TextureProfile textureProfile = TextureUtil.getTextureProfileByPath(task.lastInput(), task.input(0).getPath());
        logger.fine("Compiling %s using profile %s", task.input(0).getPath(),
                textureProfile != null ? textureProfile.getName() : "<none>");

        try {
            boolean compress = project.option("texture-compression", "false").equals("true");
            TextureGenerator.GenerateResult generateResult = TextureUtil.createMultiPageTexture(result.images,
                    textureImageType, textureProfile, compress);
            task.output(0).setContent(textureSet.toByteArray());
            TextureUtil.writeGenerateResultToResource(generateResult, task.output(1));
        } catch (TextureGeneratorException e) {
            throw new CompileExceptionError(task.input(0), -1, e.getMessage(), e);
        }
    }
}

