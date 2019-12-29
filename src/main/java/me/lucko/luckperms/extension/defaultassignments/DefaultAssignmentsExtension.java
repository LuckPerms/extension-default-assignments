package me.lucko.luckperms.extension.defaultassignments;

import com.google.common.collect.ImmutableList;
import me.lucko.luckperms.common.api.LuckPermsApiProvider;
import me.lucko.luckperms.common.api.implementation.ApiUser;
import me.lucko.luckperms.common.config.AbstractConfiguration;
import me.lucko.luckperms.common.config.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.util.ImmutableCollectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.player.PlayerLoginProcessEvent;
import net.luckperms.api.extension.Extension;

import java.lang.reflect.Field;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class DefaultAssignmentsExtension implements Extension {

    private static ScriptEngine engine = null;
    private final LuckPerms luckPerms;
    private EventSubscription<PlayerLoginProcessEvent> listener;

    public DefaultAssignmentsExtension(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    @Override
    public void load() {
        if (!(this.luckPerms instanceof LuckPermsApiProvider)) {
            throw new RuntimeException("Unexpected API implementation: " + this.luckPerms.getClass().getName());
        }

        // get the config adapter so we can read the yaml/hocon file directly
        ConfigurationAdapter config;
        try {
            config = getConfigurationAdapter((LuckPermsApiProvider) this.luckPerms);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // get a list of rules from the config
        ImmutableList<AssignmentRule> rules = config.getKeys("default-assignments", ImmutableList.of()).stream().map(name -> {
            String hasTrue = config.getString("default-assignments." + name + ".if.has-true", null);
            String hasFalse = config.getString("default-assignments." + name + ".if.has-false", null);
            String lacks = config.getString("default-assignments." + name + ".if.lacks", null);
            List<String> give = ImmutableList.copyOf(config.getStringList("default-assignments." + name + ".give", ImmutableList.of()));
            List<String> take = ImmutableList.copyOf(config.getStringList("default-assignments." + name + ".take", ImmutableList.of()));
            String pg = config.getString("default-assignments." + name + ".set-primary-group", null);
            return new AssignmentRule(hasTrue, hasFalse, lacks, give, take, pg);
        }).collect(ImmutableCollectors.toList());

        // if there are no rules present, don't bother to setup a connection listener
        if (rules.isEmpty()) {
            return;
        }

        // setup a listener to apply default assignment rules when players login
        this.listener = this.luckPerms.getEventBus().subscribe(PlayerLoginProcessEvent.class, event -> {
            if (event.getUser() == null) {
                return;
            }

            User user = ApiUser.cast(event.getUser());

            boolean saveRequired = false;
            for (AssignmentRule rule : rules) {
                if (rule.apply(user)) {
                    saveRequired = true;
                }
            }

            // If they were given a default, persist the new assignments back to the storage.
            if (saveRequired) {
                this.luckPerms.getUserManager().saveUser(event.getUser()).join();
            }
        });
    }


    @Override
    public void unload() {
        if (this.listener != null) {
            this.listener.close();
        }
    }

    private ConfigurationAdapter getConfigurationAdapter(LuckPermsApiProvider luckPerms) throws Exception {
        Field apiProviderPluginField = LuckPermsApiProvider.class.getDeclaredField("plugin");
        apiProviderPluginField.setAccessible(true);
        LuckPermsPlugin plugin = (LuckPermsPlugin) apiProviderPluginField.get(luckPerms);

        AbstractConfiguration configuration = (AbstractConfiguration) plugin.getConfiguration();

        Field configurationAdapterField = AbstractConfiguration.class.getDeclaredField("adapter");
        configurationAdapterField.setAccessible(true);
        return (ConfigurationAdapter) configurationAdapterField.get(configuration);
    }

    static synchronized ScriptEngine getScriptEngine() {
        if (engine == null) {
            engine = new ScriptEngineManager(null).getEngineByName("nashorn");
        }
        return engine;
    }

}
