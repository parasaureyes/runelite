package com.scaperune.mirror;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("scaperune")
public interface ScapeRuneConfig extends Config
{
    @ConfigItem(keyName = "mirrorWorld", name = "Mirror world",
        description = "Mirror the world using the client's scene-mirror capability.")
    default boolean mirrorWorld()
    {
        return true;
    }
}
