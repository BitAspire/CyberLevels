package com.bitaspire.cyberlevels;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import com.bitaspire.file.YAMLFile;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

class DependencyLoader {

    static final DependencyLoader BUKKIT_LOADER =
            new DependencyLoader(Bukkit.getWorldContainer(), "libraries") {
                @Override
                public void setComplexStructure(boolean complex) {
                    throw new IllegalStateException("Structure can't be changed.");
                }
            };

    static final String[] MAVEN_REPO_URLS = {
            "https://repo1.maven.org/maven2/",
            "https://repo.maven.apache.org/maven2/"
    };

    private final File librariesFolder;

    @Setter
    private boolean complexStructure = true;

    private DependencyLoader(File folder, String newName) {
        this.librariesFolder = StringUtils.isBlank(newName) ? folder : new File(folder, newName);
    }

    private void log(Log log, String message) {
        Bukkit.getLogger().log(log.level, "[DependencyLoader] " + message);
    }

    private boolean downloadFile(String urlString, File destiny) throws IOException {
        URL url = new URL(urlString);

        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log(Log.ERROR, "URL not reachable: " + urlString);
                return false;
            }
        } catch (Exception e) {
            log(Log.ERROR, "URL not reachable: " + urlString);
            e.printStackTrace();
            return false;
        }

        try (FileOutputStream out = new FileOutputStream(destiny);
             InputStream in = url.openStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        }
    }

    public final boolean load(String group, String artifact, String version, String repoUrl, boolean replace) {
        repoUrl = repoUrl != null ? repoUrl : MAVEN_REPO_URLS[0];

        try {
            String path = group.replace('.', File.separatorChar) + File.separatorChar
                    + artifact + File.separatorChar + version;
            String jarName = artifact + "-" + version + ".jar";

            File jarFile = new File(librariesFolder,
                    (complexStructure ? (path + File.separatorChar) : "") + jarName);
            if (!jarFile.exists() || replace) {
                if (jarFile.exists()) jarFile.delete();

                log(Log.GOOD, "Downloading: " + jarName);
                jarFile.getParentFile().mkdirs();

                StringBuilder builder = new StringBuilder(repoUrl);
                if (!repoUrl.endsWith("/")) builder.append('/');

                builder.append(path.replace(File.separatorChar, '/'))
                        .append('/')
                        .append(jarName);

                if (!downloadFile(builder.toString(), jarFile))
                    return false;

                if (jarFile.length() == 0) {
                    log(Log.ERROR, "Download failed or file is empty: " + jarName);
                    return false;
                }
            }

            Utils.load0(jarFile);
            log(Log.GOOD, "Loaded: " + jarName);
            return true;
        } catch (Exception e) {
            log(Log.ERROR, "Error loading dependency: " + artifact + " v" + version);
            e.printStackTrace();
            return false;
        }
    }

    public final boolean load(String group, String artifact, String version, boolean replace) {
        return load(group, artifact, version, null, replace);
    }

    public final boolean load(String group, String artifact, String version, String repoUrl) {
        return load(group, artifact, version, repoUrl, false);
    }

    public final boolean load(String group, String artifact, String version) {
        return load(group, artifact, version, false);
    }

    public boolean loadFromConfiguration(FileConfiguration c) {
        List<Map<?, ?>> dependencies = c.getMapList("dependencies");
        boolean loadAtLeastOne = false;

        for (Map<?, ?> map : dependencies) {
            String group = (String) map.get("group");
            String artifact = (String) map.get("artifact");
            String version = (String) map.get("version");

            if (group == null || artifact == null || version == null) {
                log(Log.BAD, "Invalid dependency: " + map);
                continue;
            }

            Boolean replace = (Boolean) map.get("replace");
            loadAtLeastOne = load(
                    group, artifact, version, (String) map.get("repo"),
                    replace != null && replace
            );
        }

        return loadAtLeastOne;
    }

    public boolean loadFromFile(File file) {
        if (!file.getAbsolutePath().endsWith(".yml")) {
            log(Log.BAD, file + " isn't a valid .yml file.");
            return false;
        }

        if (!file.exists()) {
            log(Log.BAD, file + " doesn't exist.");
            return false;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return loadFromConfiguration(config);
    }

    public boolean loadFromYAML(YAMLFile file) {
        return loadFromConfiguration(file.getConfiguration());
    }

    public static DependencyLoader fromFolder(File librariesFolder, String folderName) {
        return new DependencyLoader(librariesFolder, folderName);
    }

    public static DependencyLoader fromFolder(File librariesFolder) {
        return fromFolder(librariesFolder, null);
    }

    @RequiredArgsConstructor
    private enum Log {
        GOOD(Level.INFO),
        BAD(Level.WARNING),
        ERROR(Level.SEVERE);

        private final Level level;
    }

    @UtilityClass
    private static class Utils {

        private final Unsafe THE_UNSAFE = ((Supplier<Unsafe>) () -> {
            Field field;
            try {
                field = Unsafe.class.getDeclaredField("theUnsafe");
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
            field.setAccessible(true);
            try {
                return (Unsafe) field.get(null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }).get();

        Field findField(Class<?> clazz, String field) {
            try {
                return clazz.getDeclaredField(field);
            } catch (Exception e) {
                Class<?> s = clazz.getSuperclass();
                return s == null ? null : findField(s, field);
            }
        }

        @SuppressWarnings("unchecked")
        void load0(File file) throws Exception {
            final ClassLoader mainLoader = DependencyLoader.class.getClassLoader();

            Field field = findField(mainLoader.getClass(), "ucp");
            if (field == null)
                throw new IllegalStateException("Couldn't find URLClassLoader field 'ucp'");

            long offset = THE_UNSAFE.objectFieldOffset(field);
            Object ucp = THE_UNSAFE.getObject(mainLoader, offset);

            field = ucp.getClass().getDeclaredField("path");
            offset = THE_UNSAFE.objectFieldOffset(field);
            Collection<URL> paths = (Collection<URL>) THE_UNSAFE.getObject(ucp, offset);

            try {
                field = ucp.getClass().getDeclaredField("unopenedUrls");
            } catch (NoSuchFieldException e) {
                field = ucp.getClass().getDeclaredField("urls");
            }

            offset = THE_UNSAFE.objectFieldOffset(field);
            Collection<URL> urls = (Collection<URL>) THE_UNSAFE.getObject(ucp, offset);

            URL url = file.toURI().toURL();
            if (paths.contains(url)) return;

            paths.add(url);
            urls.add(url);
        }
    }
}
