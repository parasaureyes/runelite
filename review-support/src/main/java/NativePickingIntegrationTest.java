package net.runelite.client.plugins.gpu;

import net.runelite.client.game.SceneMirrorPicking;
import net.runelite.client.game.SceneMirrorViewport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import sun.misc.Unsafe;

/**
 * Exercises the exact native setters in the offline patched injected JAR.
 * Does not construct a Client, attach listeners, initialize a game, or send actions.
 * xk/fm/tk/ei and ei's superclass initializers were audited separately.
 */
public final class NativePickingIntegrationTest
{
	private static final SceneMirrorPicking mirrorPicking = new SceneMirrorPicking();
	private static int assertions;
	private static int callbackCalls;

	public static void main(String[] args) throws Exception
	{
		net.runelite.client.callback.SceneMirrorNativeAdapter.setCallbacks(
			(net.runelite.api.hooks.Callbacks) Proxy.newProxyInstance(Client.class.getClassLoader(),
				new Class<?>[]{net.runelite.api.hooks.Callbacks.class}, (proxy, method, arguments) ->
				{
					callbackCalls++;
					if (method.getName().equals("mapSceneMouseX"))
					{
						return mirrorPicking.mapSceneX((int) arguments[0], (int) arguments[1]);
					}
					if (method.getName().equals("mapSceneCanvasX"))
					{
						return mirrorPicking.mapWorldAnchorX((int) arguments[0]);
					}
					throw new AssertionError(method.getName());
				}));
		Class<?> mouse = Class.forName("tk");
		Map<String, Object> rawBefore = primitiveStatics(mouse);
		Class<?> picker = Class.forName("fm");
		Class<?> setter = Class.forName("xk");
		Method hover = setter.getDeclaredMethod("al", int.class, int.class, int.class);
		hover.setAccessible(true);

		// A native Scene's constructor creates the world. Allocate test-only storage
		// instead: dt itself writes just the plane and two scene pointer integers.
		Class<?> sceneType = Class.forName("ei");
		Object sceneA = unsafe().allocateInstance(sceneType);
		Object sceneB = unsafe().allocateInstance(sceneType);
		Method tile = sceneType.getDeclaredMethod("dt", int.class, int.class, int.class);
		tile.setAccessible(true);
		Method triangle = sceneType.getDeclaredMethod("au", int.class, int.class, int.class, int.class,
			int.class, int.class, int.class, int.class);
		triangle.setAccessible(true);

		mirrorPicking.clear();
		hover.invoke(null, 10, 15, 0);
		equal(10, decoded(picker, "bw", 1035287267), "OFF native entity x");
		equal(15, decoded(picker, "bm", 1684153271), "OFF native entity y");
		tile.invoke(sceneA, 2, 10, 15);
		equal(2, integer(sceneA, "ce"), "OFF native tile plane");
		equal(10, integer(sceneA, "cz"), "OFF native tile x");
		equal(15, integer(sceneA, "cf"), "OFF native tile y");
		equal(false, triangle.invoke(null, integer(sceneA, "cz"), integer(sceneA, "cf"),
			0, 0, 60, 480, 512, 480), "OFF native terrain triangle misses opposite-side target");

		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
			new Class<?>[]{Client.class}, (proxy, method, arguments) ->
			{
				switch (method.getName())
				{
					case "getViewportXOffset":
					case "getViewportYOffset":
						return 4;
					case "getViewportWidth":
						return 512;
					case "getViewportHeight":
						return 334;
					case "setInvertYaw":
						return null;
					case "getCanvasWidth":
						return 768;
					case "getCanvasHeight":
						return 512;
					case "isStretchedEnabled":
						return false;
					case "toString":
						return "Offline viewport fixture";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == arguments[0];
					default:
						throw new AssertionError("Unexpected Client call in offline fixture: " + method.getName());
				}
			});
		mirrorPicking.publish(client, new SceneMirrorPicking.Frame(
			4, 4, 512, 334, 4, 512, 1.0, 1.0,
			SceneMirrorViewport.capture(4, 174, 512, 334, 768, 512)));

		// Native encoding, entity-list clearing and cache invalidation must survive
		// patching; all of these effects are performed by the original method body.
		field(picker, "cp").setInt(null, 7);
		field(picker, "bl").setBoolean(null, true);
		((long[]) field(picker, "cd").get(null))[0] = 9876L;
		((int[]) field(picker, "ce").get(null))[0] = 42;
		hover.invoke(null, 10, 15, 0);
		equal(501, decoded(picker, "bw", 1035287267), "ON native entity x");
		equal(15, decoded(picker, "bm", 1684153271), "ON native entity y");
		equal(0, field(picker, "cp").getInt(null), "native entity count reset");
		equal(true, field(picker, "bd").getBoolean(null), "native viewport gate retained");
		equal(false, field(picker, "bl").getBoolean(null), "native ray cache reset");
		equal(-1L, ((long[]) field(picker, "cd").get(null))[0], "native entity hash sentinel");
		equal(Integer.MAX_VALUE, ((int[]) field(picker, "ce").get(null))[0], "native depth sentinel");

		tile.invoke(sceneA, 2, 10, 15);
		tile.invoke(sceneB, 1, 200, 100);
		equal(2, integer(sceneA, "ce"), "ON first worldview plane");
		equal(501, integer(sceneA, "cz"), "ON first worldview x");
		equal(15, integer(sceneA, "cf"), "ON first worldview y");
		equal(1, integer(sceneB, "ce"), "ON second worldview plane");
		equal(311, integer(sceneB, "cz"), "ON second worldview x");
		equal(100, integer(sceneB, "cf"), "ON second worldview y");
		equal(501, integer(sceneA, "cz"), "second worldview does not overwrite first");
		equal(true, triangle.invoke(null, integer(sceneA, "cz"), integer(sceneA, "cf"),
			0, 0, 60, 480, 512, 480), "ON mapped pointer reaches native terrain triangle");

		// The rasterizer writes hovered scene tile indices after a triangle hit.
		// Exercise the original native latch/readiness methods with those indices;
		// deliberately do not invoke menu actions or the native packet-processing tick.
		field(sceneType, "ck").setInt(sceneA, 7);
		field(sceneType, "bz").setInt(sceneA, 9);
		field(sceneType, "cw").setBoolean(sceneA, true);
		equal(7, integer(sceneA, "ck"), "native hovered tile x fixture");
		equal(9, integer(sceneA, "bz"), "native hovered tile y fixture");
		invokeNoArgs(sceneA, "ak", void.class);
		equal(7, integer(sceneA, "be"), "native selected tile x latch");
		equal(9, integer(sceneA, "bn"), "native selected tile y latch");
		equal(false, field(sceneType, "cw").getBoolean(sceneA), "native pending latch clears");
		equal(false, invokeNoArgs(sceneA, "ka", boolean.class), "native selection is not a walk request before click flag");
		invokeNoArgs(sceneA, "dp", void.class);
		equal(true, invokeNoArgs(sceneA, "ka", boolean.class), "native walk selection readiness");
		field(sceneType, "ck").setInt(sceneA, 11);
		invokeNoArgs(sceneA, "ak", void.class);
		equal(7, integer(sceneA, "be"), "native saved selection stays fixed without pending click");

		int[][] outside = {{-1, -1}, {-1, 10}, {512, 10}, {10, -1}, {10, 334}};
		for (int[] point : outside)
		{
			hover.invoke(null, point[0], point[1], 0);
			equal(point[0], decoded(picker, "bw", 1035287267), "native outside entity x unchanged");
			equal(point[1], decoded(picker, "bm", 1684153271), "native outside entity y unchanged");
			tile.invoke(sceneA, 3, point[0], point[1]);
			equal(point[0], integer(sceneA, "cz"), "native outside tile x unchanged");
			equal(point[1], integer(sceneA, "cf"), "native outside tile y unchanged");
		}

		// Native callers perform viewport eligibility before these setters. Calling
		// setters directly here tests preservation of the inputs, not new UI gating.
		hover.invoke(null, 0, 0, 0);
		equal(511, decoded(picker, "bw", 1035287267), "native left edge");
		hover.invoke(null, 511, 333, 0);
		equal(0, decoded(picker, "bw", 1035287267), "native right edge");
		mirrorPicking.clear();
		hover.invoke(null, 10, 15, 0);
		equal(10, decoded(picker, "bw", 1035287267), "clear restores native entity input");
		tile.invoke(sceneA, 2, 10, 15);
		equal(10, integer(sceneA, "cz"), "clear restores native tile input");
		equal(rawBefore, primitiveStatics(mouse), "all native raw/UI mouse scalars unchanged");
		if (callbackCalls == 0) throw new AssertionError("Native methods bypassed callbacks");
		System.out.println("Native picking setter integration: " + assertions + " assertions passed; no injected Client constructed or initialized.");
	}

	private static Object invokeNoArgs(Object object, String name, Class<?> returnType) throws Exception
	{
		// The obfuscated JVM class contains methods distinguished only by their
		// return descriptors (ak()I versus ak()V, ka()V versus ka()Z). Java's
		// getDeclaredMethod(name, parameters) cannot select those descriptors.
		for (Method method : object.getClass().getDeclaredMethods())
		{
			if (method.getName().equals(name) && method.getParameterCount() == 0
				&& method.getReturnType() == returnType && !Modifier.isStatic(method.getModifiers()))
			{
				method.setAccessible(true);
				return method.invoke(object);
			}
		}
		throw new NoSuchMethodException(name + "() returning " + returnType.getName());
	}

	private static Unsafe unsafe() throws Exception
	{
		Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
		singleton.setAccessible(true);
		return (Unsafe) singleton.get(null);
	}

	private static Map<String, Object> primitiveStatics(Class<?> type) throws Exception
	{
		Map<String, Object> values = new LinkedHashMap<>();
		for (Field f : type.getDeclaredFields())
		{
			if (Modifier.isStatic(f.getModifiers()) && f.getType().isPrimitive())
			{
				f.setAccessible(true);
				values.put(f.getName(), f.get(null));
			}
		}
		return values;
	}

	private static Field field(Class<?> type, String name) throws Exception
	{
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	private static int decoded(Class<?> type, String name, int multiplier) throws Exception
	{
		return field(type, name).getInt(null) * multiplier;
	}

	private static int integer(Object object, String name) throws Exception
	{
		return field(object.getClass(), name).getInt(object);
	}

	private static void equal(Object expected, Object actual, String label)
	{
		assertions++;
		if (!expected.equals(actual))
		{
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}
}
