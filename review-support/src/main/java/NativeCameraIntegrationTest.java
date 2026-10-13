package net.runelite.client.plugins.gpu;

import net.runelite.client.game.SceneMirrorPicking;
import net.runelite.client.game.SceneMirrorViewport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import net.runelite.api.Client;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Executes the pinned client.sc camera method in an initializer-free bytecode capsule.
 * No native Client class is defined, constructed or initialized. The copied method
 * retains its arithmetic and branch instructions; only storage/dependency owners
 * change. Real audited tk pointer/button fields and their native encodings remain.
 * This validates native camera math plus the production preference bridge, not a
 * launched client's complete camera/render pipeline.
 */
public final class NativeCameraIntegrationTest
{
	private static final String SOURCE_SHA256 = "F5364308262C7FC42309174C6A96D6129C7B82146BE22C5935123B7EA8700A15";
	private static final String CAPSULE = "net/runelite/client/plugins/gpu/NativeCameraCapsule";
	private static final String KEYS = "net/runelite/client/plugins/gpu/NativeCameraIntegrationTest$KeyState";
	private static final BigInteger MODULUS = BigInteger.ONE.shiftLeft(32);
	private static Class<?> capsule;
	private static Class<?> mouse;
	private static Method cameraTick;
	private static Method nativeYawSetter;
	private static final SceneMirrorPicking mirrorPicking = new SceneMirrorPicking();
	private static int assertions;
	private static final KeyState keyState = new KeyState();

	public static void main(String[] args) throws Exception
	{
		if (args.length != 1)
		{
			throw new IllegalArgumentException("Usage: NativeCameraIntegrationTest original-injected-client.jar");
		}
		capsule = extract(Path.of(args[0]));
		// tk's initializer creates an unattached Object-only listener and scalar defaults.
		// No tk mouse/focus callback, listener registration or event synthesis is used.
		mouse = Class.forName("tk");
		cameraTick = capsule.getMethod("sc", int.class);
		nativeYawSetter = capsule.getMethod("setInvertYaw", boolean.class);
		field("client_ez").set(null, keyState);
		Client client = fixtureClient();
		try
		{
			for (boolean preference : new boolean[]{false, true})
			{
				for (boolean invertPitch : new boolean[]{false, true})
				{
					mirrorPicking.clear();
					mirrorPicking.setCameraYawPreference(client, preference);
					equal(preference, field("client_qy").getBoolean(null), "base preference on native display");
					verifyTick(112, 107, 4, 0, true, invertPitch, 1,
						preference ? 24 : -24, invertPitch ? -14 : 14, 106, 103,
						"native display drag");
					SceneMirrorPicking.Frame frame = frame();
					mirrorPicking.beginFrame(client, frame);
					equal(preference, field("client_qy").getBoolean(null), "unpresented frame does not invert camera");
					mirrorPicking.presentFrame(client, frame);
					equal(!preference, field("client_qy").getBoolean(null), "displayed mirror XOR base preference");
					verifyTick(112, 107, 4, 0, true, invertPitch, 1,
						preference ? -24 : 24, invertPitch ? -14 : 14, 106, 103,
						"displayed mirror drag");
					mirrorPicking.beginFrame(client, null);
					equal(!preference, field("client_qy").getBoolean(null), "render toggle alone preserves displayed camera state");
					mirrorPicking.presentFrame(client, null);
					equal(preference, field("client_qy").getBoolean(null), "presented native frame restores preference");
				}
			}
			mirrorPicking.clear();
			mirrorPicking.setCameraYawPreference(client, false);
			verifyTick(100, 100, 4, 0, true, false, 1, 0, 0, 100, 100, "drag begins without a jump");
			verifyTick(99, 101, 4, 0, true, false, 1, 2, 2, 99, 101, "one-pixel drag native snap branch");
			verifyTick(112, 107, 4, 0, true, false, .5f, -12, 7, 106, 103, "native camera speed retained");
			verifyTick(112, 107, 1, 1 << 1, false, false, 1, -24, 14, 106, 103, "custom camera button mask");
			verifyIdle(1, 0, true, "ordinary left button does not become camera drag");
			verifyIdle(4, 0, false, "middle-button preference disabled");
			verifyIdle(0, 0, true, "released camera button decays native deltas");
			verifyIdle(4, 1 << 1, true, "custom mask excludes middle button");

			for (int key : new int[]{96, 97, 98, 99})
			{
				mirrorPicking.clear();
				mirrorPicking.setCameraYawPreference(client, false);
				verifyKey(key, "native keyboard camera");
				mirrorPicking.publish(client, frame());
				equal(true, field("client_qy").getBoolean(null), "mirror inversion active before keyboard test");
				verifyKey(key, "mirrored keyboard branch remains native");
			}

			mirrorPicking.setCameraYawPreference(client, true);
			equal(false, field("client_qy").getBoolean(null), "preference change while mirrored recomputes XOR");
			mirrorPicking.clear();
			equal(true, field("client_qy").getBoolean(null), "clear restores latest base preference");
			System.out.println("Native camera bytecode capsule: " + assertions
				+ " assertions passed; actual tk scalars unchanged; no native Client initialization.");
		}
		finally
		{
			mirrorPicking.clear();
			mirrorPicking.setDisplayTransformSupplier(null);
		}
	}

	private static void verifyTick(int x, int y, int button, int mask, boolean middleEnabled,
		boolean invertPitch, float speed, int yaw, int pitch, int previousX, int previousY, String label) throws Exception
	{
		seed(x, y, button, mask, middleEnabled, invertPitch, speed);
		Map<String, Object> raw = primitiveStatics(mouse);
		cameraTick.invoke(null, 0);
		equal(yaw, field("client_ym").getInt(null), label + " yaw");
		equal(pitch, field("client_sk").getInt(null), label + " pitch");
		equal(previousX, field("client_ev").getInt(null), label + " previous x");
		equal(previousY, field("client_vf").getInt(null), label + " previous y");
		equal(invertPitch, field("client_hz").getBoolean(null), label + " pitch preference unchanged");
		equal(raw, primitiveStatics(mouse), label + " all raw/UI pointer, button and timestamp scalars unchanged");
	}

	private static void verifyIdle(int button, int mask, boolean middleEnabled, String label) throws Exception
	{
		seed(112, 107, button, mask, middleEnabled, false, 1);
		field("client_ym").setInt(null, 20);
		field("client_sk").setInt(null, 18);
		Map<String, Object> raw = primitiveStatics(mouse);
		cameraTick.invoke(null, 0);
		equal(10, field("client_ym").getInt(null), label + " yaw decays");
		equal(9, field("client_sk").getInt(null), label + " pitch decays");
		equal(112, field("client_ev").getInt(null), label + " native previous x follows raw pointer");
		equal(107, field("client_vf").getInt(null), label + " native previous y follows raw pointer");
		equal(raw, primitiveStatics(mouse), label + " raw scalars unchanged");
	}

	private static void verifyKey(int key, String label) throws Exception
	{
		seed(112, 107, 0, 0, true, true, 1);
		keyState.pressed[key] = true;
		Map<String, Object> raw = primitiveStatics(mouse);
		cameraTick.invoke(null, 0);
		equal(key == 96 ? -12 : key == 97 ? 12 : 0, field("client_ym").getInt(null), label + " yaw key=" + key);
		equal(key == 98 ? 6 : key == 99 ? -6 : 0, field("client_sk").getInt(null), label + " pitch key=" + key);
		equal(raw, primitiveStatics(mouse), label + " raw scalars unchanged");
	}

	private static void seed(int x, int y, int button, int mask, boolean middleEnabled, boolean invertPitch, float speed) throws Exception
	{
		for (int i = 0; i < keyState.pressed.length; ++i)
		{
			keyState.pressed[i] = false;
		}
		rawField("af").setInt(null, encode(x, 3463869));
		rawField("ac").setInt(null, encode(y, -2093701733));
		rawField("aj").setInt(null, encode(button, 1569107801));
		field("client_ev").setInt(null, 100);
		field("client_vf").setInt(null, 100);
		field("client_ym").setInt(null, 0);
		field("client_sk").setInt(null, 0);
		field("client_xz").setInt(null, mask);
		field("bn_hc").setBoolean(null, middleEnabled);
		field("client_hz").setBoolean(null, invertPitch);
		field("client_ri").setFloat(null, speed);
	}

	private static Client fixtureClient()
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "setInvertYaw":
					nativeYawSetter.invoke(null, args[0]);
					return null;
				case "getViewportXOffset":
				case "getViewportYOffset":
					return 4;
				case "getViewportWidth":
					return 512;
				case "getViewportHeight":
					return 334;
				case "getCanvasWidth":
					return 768;
				case "getCanvasHeight":
					return 512;
				case "isStretchedEnabled":
					return false;
				case "toString":
					return "Offline camera fixture";
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				default:
					throw new AssertionError("Unexpected Client call in offline camera fixture: " + method.getName());
			}
		});
	}

	private static SceneMirrorPicking.Frame frame()
	{
		return new SceneMirrorPicking.Frame(4, 4, 512, 334, 4, 512, 1, 1,
			SceneMirrorViewport.capture(4, 174, 512, 334, 768, 512));
	}

	private static Class<?> extract(Path source) throws Exception
	{
		StringBuilder actual = new StringBuilder();
		for (byte value : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))
		{
			actual.append(String.format("%02X", value & 255));
		}
		if (!SOURCE_SHA256.equals(actual.toString()))
		{
			throw new IllegalArgumentException("Unrecognized injected-client build; refusing camera capsule");
		}
		ClassNode original = new ClassNode();
		try (ZipFile jar = new ZipFile(source.toFile()))
		{
			new ClassReader(jar.getInputStream(jar.getEntry("client.class"))).accept(original, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
		ClassNode result = new ClassNode();
		result.version = Opcodes.V11;
		result.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
		result.name = CAPSULE;
		result.superName = "java/lang/Object";
		Map<String, String> fields = new LinkedHashMap<>();
		for (String[] target : new String[][]{{"sc", "(I)V"}, {"getMouseCurrentButton", "()I"}, {"setInvertYaw", "(Z)V"}})
		{
			MethodNode method = copyMethod(original, target[0], target[1]);
			boolean yawSetter = method.name.equals("setInvertYaw");
			method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
			for (AbstractInsnNode instruction : method.instructions.toArray())
			{
				if (instruction instanceof TypeInsnNode)
				{
					throw new IllegalStateException("Unexpected allocation/type dependency in native camera method");
				}
				if (instruction instanceof VarInsnNode && yawSetter)
				{
					VarInsnNode variable = (VarInsnNode) instruction;
					if (variable.getOpcode() != Opcodes.ILOAD || variable.var != 1)
					{
						throw new IllegalStateException("Unexpected native yaw setter locals");
					}
					variable.var = 0;
				}
				if (instruction instanceof FieldInsnNode)
				{
					FieldInsnNode field = (FieldInsnNode) instruction;
					if (field.owner.equals("tk"))
					{
						if (field.getOpcode() != Opcodes.GETSTATIC || !field.desc.equals("I")
							|| !(field.name.equals("af") || field.name.equals("ac") || field.name.equals("aj")))
						{
							throw new IllegalStateException("Native camera unexpectedly modifies raw mouse state");
						}
						continue;
					}
					if (field.owner.equals("fz") && field.name.equals("cw") && field.desc.equals("Lclient;"))
					{
						AbstractInsnNode next = instruction.getNext();
						if (!(next instanceof MethodInsnNode) || !((MethodInsnNode) next).name.equals("getMouseCurrentButton"))
						{
							throw new IllegalStateException("Unexpected client receiver dependency");
						}
						method.instructions.remove(instruction);
						continue;
					}
					if (!(field.owner.equals("client") || field.owner.equals("bn")))
					{
						throw new IllegalStateException("Unexpected camera field owner: " + field.owner);
					}
					String name = field.owner + "_" + field.name;
					if (field.owner.equals("client") && field.name.equals("ez") && field.desc.equals("Lfd;"))
					{
						field.desc = "L" + KEYS + ";";
					}
					else if (!(field.desc.equals("I") || field.desc.equals("F") || field.desc.equals("Z")))
					{
						throw new IllegalStateException("Unexpected native camera object field: " + name);
					}
					fields.put(name, field.desc);
					field.owner = CAPSULE;
					field.name = name;
				}
				if (instruction instanceof MethodInsnNode)
				{
					MethodInsnNode call = (MethodInsnNode) instruction;
					if (call.owner.equals("client") && call.name.equals("getMouseCurrentButton") && call.desc.equals("()I"))
					{
						call.owner = CAPSULE;
						call.setOpcode(Opcodes.INVOKESTATIC);
					}
					else if (call.owner.equals("fd") && call.name.equals("oc") && call.desc.equals("(I)Z"))
					{
						call.owner = KEYS;
					}
					else
					{
						throw new IllegalStateException("Unexpected native camera method dependency: " + call.owner + "." + call.name);
					}
				}
			}
			result.methods.add(method);
		}
		for (Map.Entry<String, String> field : fields.entrySet())
		{
			result.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field.getKey(), field.getValue(), null, null));
		}
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		result.accept(writer);
		return new CapsuleLoader().define(writer.toByteArray());
	}

	private static MethodNode copyMethod(ClassNode owner, String name, String descriptor)
	{
		MethodNode found = null;
		for (MethodNode method : owner.methods)
		{
			if (method.name.equals(name) && method.desc.equals(descriptor))
			{
				if (found != null)
				{
					throw new IllegalStateException("Ambiguous native camera method");
				}
				found = new MethodNode();
				method.accept(found);
				found.name = name;
				found.desc = descriptor;
			}
		}
		if (found == null)
		{
			throw new IllegalStateException("Missing native camera method: " + name + descriptor);
		}
		return found;
	}

	public static final class KeyState
	{
		final boolean[] pressed = new boolean[112];

		public boolean oc(int key)
		{
			return pressed[key];
		}
	}

	private static final class CapsuleLoader extends ClassLoader
	{
		CapsuleLoader()
		{
			super(NativeCameraIntegrationTest.class.getClassLoader());
		}

		Class<?> define(byte[] bytes)
		{
			return defineClass(CAPSULE.replace('/', '.'), bytes, 0, bytes.length);
		}
	}

	private static Field field(String name) throws Exception
	{
		return capsule.getField(name);
	}

	private static Field rawField(String name) throws Exception
	{
		Field field = mouse.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static int encode(int value, int decoder)
	{
		return value * BigInteger.valueOf(decoder).mod(MODULUS).modInverse(MODULUS).intValue();
	}

	private static Map<String, Object> primitiveStatics(Class<?> type) throws Exception
	{
		Map<String, Object> result = new LinkedHashMap<>();
		for (Field field : type.getDeclaredFields())
		{
			if (Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive())
			{
				field.setAccessible(true);
				result.put(field.getName(), field.get(null));
			}
		}
		return result;
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
