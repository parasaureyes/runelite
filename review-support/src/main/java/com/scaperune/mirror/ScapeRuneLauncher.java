package com.scaperune.mirror;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class ScapeRuneLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ScapeRunePlugin.class);
        RuneLite.main(args);
    }
}
