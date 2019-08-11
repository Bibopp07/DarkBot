package com.github.manolo8.darkbot.extensions.plugins;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.extensions.util.Version;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class PluginHandler {
    private static final Gson GSON = new Gson();

    public static final File PLUGIN_FOLDER = new File("plugins"),
            PLUGIN_UPDATE_FOLDER = new File("plugins/updates");
    public static final Path PLUGIN_PATH = PLUGIN_FOLDER.toPath(),
            PLUGIN_UPDATE_PATH = PLUGIN_UPDATE_FOLDER.toPath();

    public boolean isLoading;

    public URLClassLoader PLUGIN_CLASS_LOADER;
    public List<Plugin> LOADED_PLUGINS = new ArrayList<>();

    private static List<PluginListener> LISTENERS = new ArrayList<>();

    public void addListener(PluginListener listener) {
        LISTENERS.add(listener);
    }

    private File[] getJars(String folder) {
        File[] jars = new File(folder).listFiles((dir, name) -> name.endsWith(".jar"));
        return jars != null ? jars : new File[0];
    }

    public void updatePlugins() {
        isLoading = true;
        LISTENERS.forEach(PluginListener::beforeLoad);

        if (PLUGIN_CLASS_LOADER != null) {
            try {
                PLUGIN_CLASS_LOADER.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (File plugin : getJars("plugins/updates")) {
            Path plPath = plugin.toPath();
            try {
                Files.move(plPath, PLUGIN_PATH.resolve(plPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.err.println("Failed to update plugin " + plPath);
                e.printStackTrace();
            }
        }
        try {
            loadPlugins(getJars("plugins"));
        } catch (Exception e) {
            System.err.println("Failed to load plugins");
            e.printStackTrace();
        }
        LISTENERS.forEach(PluginListener::afterLoad);
        isLoading = false;
    }

    private void loadPlugins(File[] pluginFiles) {
        LOADED_PLUGINS.clear();
        for (File plugin : pluginFiles) {
            try {
                LOADED_PLUGINS.add(loadPlugin(plugin));
            } catch (Exception e) {
                System.err.println("Could not load plugin: " + plugin.getName());
                e.printStackTrace();
            }
        }
        PLUGIN_CLASS_LOADER = new URLClassLoader(LOADED_PLUGINS.stream().map(Plugin::getJar).toArray(URL[]::new));
    }

    private Plugin loadPlugin(File plFile) throws IOException {
        JarFile jar = new JarFile(plFile);
        ZipEntry plJson = jar.getEntry("plugin.json");
        if (plJson == null) {
            throw new IllegalArgumentException("missing plugin.json");
        }
        PluginDefinition plugin = GSON.fromJson(new InputStreamReader(jar.getInputStream(plJson), StandardCharsets.UTF_8), PluginDefinition.class);

        testCompatibility(plugin);
        return new Plugin(plugin, plFile.toURI().toURL());
    }

    private void testCompatibility(PluginDefinition plugin) {
        String pluginVer = plugin.name + " v" + plugin.version + " by " + plugin.author;

        if (plugin.minVersion.compareTo(plugin.supportedVersion) > 0)
            throw new IllegalStateException(pluginVer + " minimum version can't higher than supported version");

        String supportedRange = "DarkBot v" + (plugin.minVersion.compareTo(plugin.supportedVersion) == 0 ?
                plugin.minVersion : plugin.minVersion + "-v" + plugin.supportedVersion);

        if (Main.VERSION.compareTo(plugin.minVersion) < 0)
            throw new IllegalArgumentException(pluginVer + " requires " + supportedRange);

        if (Main.VERSION.compareTo(plugin.supportedVersion) > 0)
            System.out.println(pluginVer + " is made for " + supportedRange + ", so it may not work on DarkBot v" + Main.VERSION);
        else
            System.out.println(pluginVer + " is made for " + supportedRange + ", so it should work fine on DarkBot v" + Main.VERSION);
    }


}