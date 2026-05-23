package com.defold.extension.pipeline.webp;

import com.dynamo.bob.Project;
import com.dynamo.bob.plugin.IPlugin;

public final class WebPImportPlugin implements IPlugin {
    @Override
    public void init(Project project) {
        WebPImageIO.install();
        WebPSourceMappings.install(project);
    }

    @Override
    public void exit(Project project) {
    }
}
