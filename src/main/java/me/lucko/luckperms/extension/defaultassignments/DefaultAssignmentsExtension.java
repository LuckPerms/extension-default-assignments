package me.lucko.luckperms.extension.defaultassignments;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.player.PlayerLoginProcessEvent;
import net.luckperms.api.extension.Extension;
import net.luckperms.api.model.user.User;

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
        // get a list of rules from the config
        List<AssignmentRule> rules = LuckPermsConfigAccessor.getAssignmentRules(this.luckPerms);

        // if there are no rules present, don't bother to setup a connection listener
        if (rules.isEmpty()) {
            return;
        }

        // setup a listener to apply default assignment rules when players login
        this.listener = this.luckPerms.getEventBus().subscribe(PlayerLoginProcessEvent.class, event -> {
            if (event.getUser() == null) {
                return;
            }

            User user = event.getUser();

            boolean saveRequired = false;
            for (AssignmentRule rule : rules) {
                if (rule.apply(user)) {
                    saveRequired = true;
                }
            }

            // If they were given a default, persist the new assignments back to the storage.
            if (saveRequired) {
                this.luckPerms.getUserManager().saveUser(user).join();
            }
        });
    }

    @Override
    public void unload() {
        if (this.listener != null) {
            this.listener.close();
        }
    }

    static synchronized ScriptEngine getScriptEngine() {
        if (engine == null) {
            engine = new ScriptEngineManager(null).getEngineByName("nashorn");
        }
        return engine;
    }

}
