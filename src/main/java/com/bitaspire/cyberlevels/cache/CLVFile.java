package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import me.croabeast.file.ConfigurableFile;

import java.io.IOException;

final class CLVFile extends ConfigurableFile {

    CLVFile(CyberLevels loader, String name) throws IOException {
        super(loader, name);

        setLoggerAction(loader::logger);
        saveDefaults();
        reload();
    }
}
