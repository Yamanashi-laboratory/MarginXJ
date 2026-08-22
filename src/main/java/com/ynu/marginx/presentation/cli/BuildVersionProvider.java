package com.ynu.marginx.presentation.cli;

import com.ynu.marginx.infrastructure.config.BuildVersion;
import picocli.CommandLine;

/** What {@code --version} prints: the version the running copy was actually built as. */
public final class BuildVersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {"MarginXJ " + BuildVersion.version()};
    }
}
