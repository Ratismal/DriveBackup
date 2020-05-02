package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mcstats.Metrics;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.handler.CommandHandler;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.util.MessageUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;

public class DriveBackup extends JavaPlugin {

    private String newVersionTitle = "";
    private double newVersion = 0;
    private double currentVersion = 0;
    private String currentVersionTitle = "";

    private static Config pluginconfig;
    private static DriveBackup plugin;
    public Logger log = getLogger();


    /**
     * What to do when plugin is enabled (init)
     */
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        reloadConfig();

        pluginconfig = new Config(getConfig());
        pluginconfig.reload();
        //reloadLocalConfig();
        getCommand("drivebackup").setExecutor(new CommandHandler(this));
        plugin = this;

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(), this);


        currentVersionTitle = getDescription().getVersion().split("-")[0];
        currentVersion = Double.valueOf(currentVersionTitle.replaceFirst("\\.", ""));

        if (Config.isMetrics()) {
            try {
                initMetrics();
                MessageUtil.sendConsoleMessage("Metrics started");
            } catch (IOException e) {
                MessageUtil.sendConsoleMessage("Metrics failed to start");
            }
        }

        startThread();

        /**
         * Starts update checker
         */
        this.getServer().getScheduler().runTask(this, new Runnable() {

            @Override
            public void run() {
                getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

                    @Override
                    public void run() {
                        if (Config.isUpdateCheck()) {
                            try {
                                MessageUtil.sendConsoleMessage("Running update checker...");
                                newVersion = updateCheck(currentVersion);
                                if (newVersion > currentVersion) {
                                    MessageUtil.sendConsoleMessage("Version " + newVersionTitle + " has been released." + " You are currently running version " + currentVersionTitle);
                                    MessageUtil.sendConsoleMessage("Update at: http://dev.bukkit.org/bukkit-plugins/drivebackup/");
                                } else if (currentVersion > newVersion) {
                                    MessageUtil.sendConsoleMessage("You are running an unsupported build!");
                                    MessageUtil.sendConsoleMessage("The recommended version is " + newVersionTitle + ", and you are running " + currentVersionTitle);
                                    MessageUtil.sendConsoleMessage("If the plugin has just recently updated, please ignore this message.");
                                } else {
                                    MessageUtil.sendConsoleMessage("Hooray! You are running the latest build!");
                                }
                            } catch (Exception e) {
                                // ignore exceptions
                            }
                        }
                    }
                }, 0, 430000);

            }

        });

    }

    public void initMetrics() throws IOException {
        Metrics metrics = new Metrics(this);

        Metrics.Graph enabledModes = metrics.createGraph("Enabled Services");

        enabledModes.addPlotter(new Metrics.Plotter("Google Drive") {
            @Override
            public int getValue() {
                return Config.isGoogleEnabled() ? 1 : 0;
            }
        });

        enabledModes.addPlotter(new Metrics.Plotter("OneDrive") {
            @Override
            public int getValue() {
                return Config.isOnedriveEnabled() ? 1 : 0;
            }
        });

        enabledModes.addPlotter(new Metrics.Plotter("FTP") {
            @Override
            public int getValue() {
                return Config.isFtpEnabled() ? 1 : 0;
            }
        });

        enabledModes.addPlotter(new Metrics.Plotter("None") {
            @Override
            public int getValue() {
                return Config.isOnedriveEnabled() || Config.isGoogleEnabled() || Config.isFtpEnabled() ? 0 : 1;
            }
        });

        metrics.start();
    }

    /**
     * What to do when plugin is disabled
     */
    public void onDisable() {
        MessageUtil.sendConsoleMessage("Stopping plugin!");
    }

    /**
     * Gets an instance of the plugin
     *
     * @return DriveBackup plugin
     */
    public static DriveBackup getInstance() {
        return plugin;
    }

    /**
     * Starts the backup thread
     */
    public static void startThread() {
        if (Config.getBackupDelay() / 60 / 20 != -1) {
            MessageUtil.sendConsoleMessage("Starting the backup thread for every " + Config.getBackupDelay() + " ticks.");
            BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
            scheduler.scheduleSyncRepeatingTask(getInstance(), new UploadThread(), Config.getBackupDelay(), Config.getBackupDelay());
        }
    }

    /**
     * Reloads config
     */
    public static void reloadLocalConfig() {
        getInstance().reloadConfig();
        pluginconfig.reload(getInstance().getConfig());
    }

    /**
     * Checks if there is an available update (Adapted from Vault's update checker)
     *
     * @param currentVersion Current plugin version
     * @return Latest version
     */
    public double updateCheck(double currentVersion) {
        try {
            URL url = new URL("https://api.curseforge.com/servermods/files?projectids=97321");
            URLConnection conn = url.openConnection();
            conn.setReadTimeout(5000);
            conn.addRequestProperty("User-Agent", "DriveBackup Update Checker");
            conn.setDoOutput(true);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            final String response = reader.readLine();
            final JSONArray array = new JSONArray(response);

            if (array.length() == 0) {
                this.getLogger().warning("No files found, or Feed URL is bad.");
                return currentVersion;
            }
            // Pull the last version from the JSON
            newVersionTitle = ((String) ((JSONObject) array.get(array.length() - 1)).get("name")).replace("DriveBackup-", "").trim();
            return Double.valueOf(newVersionTitle.replaceFirst("\\.", "").trim());
        } catch (Exception e) {
            MessageUtil.sendConsoleMessage("There was an issue attempting to check for the latest version.");
        }
        return currentVersion;
    }
}
