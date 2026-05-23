package com.defold.extension.pipeline.webp;

import com.dynamo.bob.Builder;
import com.dynamo.bob.BuilderParams;
import com.dynamo.bob.CompileExceptionError;
import com.dynamo.bob.Task;
import com.dynamo.bob.Task.TaskBuilder;
import com.dynamo.bob.fs.IResource;
import com.dynamo.bob.logging.Logger;
import com.dynamo.bob.pipeline.TextureGenerator;
import com.dynamo.bob.pipeline.TextureGeneratorException;
import com.dynamo.bob.util.TextureUtil;
import com.dynamo.bob.textureset.TextureSetGenerator.TextureSetResult;
import com.dynamo.gamesys.proto.TextureSetProto.TextureSet;
import com.dynamo.graphics.proto.Graphics.TextureProfile;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@BuilderParams(name = "WebPTexture", inExts = { ".webp" }, outExt = ".texturec", isCacheble = true, paramsForSignature = {
        "texture-compression" })
public final class WebPTextureBuilder extends Builder {
    private static final Logger logger = Logger.getLogger(WebPTextureBuilder.class.getName());

    @Override
    public Task create(IResource input) throws IOException {
        TaskBuilder taskBuilder = Task.newBuilder(this)
                .setName(params.name())
                .addInput(input)
                .addOutput(input.changeExt(params.outExt()))
                .addOutput(input.changeExt(".a.texturesetc"));

        TextureUtil.addTextureProfileInput(taskBuilder, project);
        return taskBuilder.build();
    }

    @Override
    public void build(Task task) throws CompileExceptionError, IOException {
        IResource input = task.input(0);

        WebPImageIO.install();

        try {
            TextureProfile textureProfile = TextureUtil.getTextureProfileByPath(task.lastInput(), input.getPath());
            logger.fine("Compiling %s using profile %s", input.getPath(),
                    textureProfile != null ? textureProfile.getName() : "<none>");

            boolean compress = project.option("texture-compression", "false").equals("true");
            WebPFrames.Decoded decoded = WebPFrames.read(input.getContent());
            TextureGenerator.GenerateResult generateResult = TextureGenerator.generate(decoded.getFirstFrame(), textureProfile, compress);

            TextureUtil.writeGenerateResultToResource(generateResult, task.output(0));

            TextureSetResult textureSetResult = WebPTextureSetUtil.generateWebPTextureSet(input);
            String texturePath = task.output(0).getPath().substring(project.getBuildDirectory().length());
            TextureSet textureSet = textureSetResult.builder
                    .setPageCount(0)
                    .setTexture(texturePath)
                    .build();
            task.output(1).setContent(textureSet.toByteArray());
        } catch (TextureGeneratorException e) {
            logger.severe(e.getMessage());
            throw new CompileExceptionError(input, -1, e.getMessage(), e);
        }
    }
}
