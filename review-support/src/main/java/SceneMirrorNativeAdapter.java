package net.runelite.client.callback;

import net.runelite.api.Client;
import net.runelite.api.hooks.Callbacks;
import net.runelite.client.RuneLite;

/** Local validation only; not part of the plugin or proposed upstream source diff. */
public final class SceneMirrorNativeAdapter
{
    private static volatile Callbacks callbacks;

    public static void setCallbacks(Callbacks value)
    {
        callbacks = value;
    }

    private static Callbacks callbacks()
    {
        Callbacks value = callbacks;
        if (value == null && RuneLite.getInjector() != null)
        {
            value = RuneLite.getInjector().getInstance(Client.class).getCallbacks();
            callbacks = value;
        }
        return value;
    }

    public static int mapSceneX(int x, int y)
    {
        Callbacks value = callbacks();
        return value == null ? x : value.mapSceneMouseX(x, y);
    }

    public static int mapWorldAnchorX(int x)
    {
        Callbacks value = callbacks();
        return value == null ? x : value.mapSceneCanvasX(x);
    }
}
