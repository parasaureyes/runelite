package com.scaperune.mirror;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.SceneMirror;

@PluginDescriptor(name = "ScapeRune", description = "Mirror the world with matching native targeting.",
    tags = {"cosmetic", "mirror"}, enabledByDefault = true)
public class ScapeRunePlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private SceneMirror mirror;
    @Inject private ScapeRuneConfig config;
    @Inject private ConfigManager configManager;
    @Inject private ClientToolbar toolbar;
    private NavigationButton navigation;
    private volatile ScapeRunePanel panel;
    private int lastState = -1;

    @Provides ScapeRuneConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(ScapeRuneConfig.class);
    }

    @Override protected void startUp()
    {
        panel = new ScapeRunePanel(enabled ->
            configManager.setConfiguration("scaperune", "mirrorWorld", enabled));
        navigation = NavigationButton.builder().tooltip("ScapeRune")
            .icon(ScapeRunePanel.createIcon()).priority(8).panel(panel).build();
        toolbar.addNavigation(navigation);
        mirror.setEnabled(config.mirrorWorld());
        clientThread.invoke(() ->
        {
            lastState = -1;
            onClientTick(null);
        });
    }

    @Override protected void shutDown()
    {
        mirror.setEnabled(false);
        panel = null;
        if (navigation != null)
        {
            toolbar.removeNavigation(navigation);
            navigation = null;
        }
    }

    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (panel != null && "scaperune".equals(event.getGroup()))
        {
            mirror.setEnabled(config.mirrorWorld());
            clientThread.invoke(() -> onClientTick(null));
        }
    }

    @Subscribe public void onClientTick(ClientTick event)
    {
        ScapeRunePanel current = panel;
        if (current == null) return;
        boolean enabled = config.mirrorWorld();
        boolean available = mirror.isAvailable();
        boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
        int state = (enabled ? 1 : 0) | (available ? 2 : 0) | (loggedIn ? 4 : 0);
        if (state == lastState) return;
        lastState = state;
        SwingUtilities.invokeLater(() ->
        {
            if (panel == current) current.updateState(enabled, available, loggedIn);
        });
    }
}
