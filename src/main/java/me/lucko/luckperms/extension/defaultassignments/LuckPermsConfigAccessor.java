package me.lucko.luckperms.extension.defaultassignments;

import com.google.common.collect.ImmutableList;

import me.lucko.luckperms.common.api.LuckPermsApiProvider;
import me.lucko.luckperms.common.config.LuckPermsConfiguration;
import me.lucko.luckperms.common.config.generic.KeyedConfiguration;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;

import net.luckperms.api.LuckPerms;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

public enum LuckPermsConfigAccessor {
    ;

    public static List<AssignmentRule> getAssignmentRules(LuckPerms luckPerms) {
        if (!(luckPerms instanceof LuckPermsApiProvider)) {
            throw new RuntimeException("Unexpected API implementation: " + luckPerms.getClass().getName());
        }

        // get the config adapter so we can read the yaml/hocon file directly
        ConfigurationAdapter config;
        try {
            config = getConfigurationAdapter((LuckPermsApiProvider) luckPerms);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // get a list of rules from the config
        return ImmutableList.copyOf(config.getKeys("default-assignments", ImmutableList.of()).stream().map(name -> {
            String hasTrue = config.getString("default-assignments." + name + ".if.has-true", null);
            String hasFalse = config.getString("default-assignments." + name + ".if.has-false", null);
            String lacks = config.getString("default-assignments." + name + ".if.lacks", null);
            List<String> give = ImmutableList.copyOf(config.getStringList("default-assignments." + name + ".give", ImmutableList.of()));
            List<String> take = ImmutableList.copyOf(config.getStringList("default-assignments." + name + ".take", ImmutableList.of()));
            String pg = config.getString("default-assignments." + name + ".set-primary-group", null);
            return new AssignmentRule(hasTrue, hasFalse, lacks, give, take, pg);
        }).collect(Collectors.toList()));
    }

    private static ConfigurationAdapter getConfigurationAdapter(LuckPermsApiProvider luckPerms) throws Exception {
        Field apiProviderPluginField = LuckPermsApiProvider.class.getDeclaredField("plugin");
        apiProviderPluginField.setAccessible(true);
        LuckPermsPlugin plugin = (LuckPermsPlugin) apiProviderPluginField.get(luckPerms);

        LuckPermsConfiguration configuration = plugin.getConfiguration();

        Field configurationAdapterField = KeyedConfiguration.class.getDeclaredField("adapter");
        configurationAdapterField.setAccessible(true);
        return (ConfigurationAdapter) configurationAdapterField.get(configuration);
    }

}
