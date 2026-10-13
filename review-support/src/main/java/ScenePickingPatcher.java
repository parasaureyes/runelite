import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Offline, exact-build patch tool. Never loads the game client. */
public final class ScenePickingPatcher
{
	private static final String SOURCE_SHA256 = "F5364308262C7FC42309174C6A96D6129C7B82146BE22C5935123B7EA8700A15";
	private static final String BRIDGE = "net/runelite/client/callback/SceneMirrorNativeAdapter";

	static void patchClass(ClassNode node)
	{
		if (node.name.equals("client"))
		{
			if (node.methods.stream().anyMatch(m -> m.name.equals("isSceneMirroringSupported")))
			{
				throw new IllegalStateException("Capability already installed");
			}
			MethodNode capability = new MethodNode(Opcodes.ACC_PUBLIC, "isSceneMirroringSupported", "()Z", null, null);
			capability.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1));
			capability.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN));
			node.methods.add(capability);
			return;
		}
		if (node.name.equals("ry"))
		{
			int anchors = 0;
			for (MethodNode method : node.methods)
			{
				if (!method.name.equals("ag") || !method.desc.equals("(IIII)V"))
				{
					continue;
				}
				for (AbstractInsnNode instruction : method.instructions.toArray())
				{
					if (instruction instanceof FieldInsnNode)
					{
						FieldInsnNode field = (FieldInsnNode) instruction;
						AbstractInsnNode encoding = instruction.getPrevious();
						if (field.getOpcode() == Opcodes.PUTSTATIC && field.owner.equals("bo") && field.name.equals("ae")
							&& encoding.getOpcode() == Opcodes.IMUL && encoding.getPrevious().getOpcode() == Opcodes.IADD)
						{
							// Only the successful world projection, before native integer encoding.
							// Invalid -1 branches and y projection stay byte-for-byte equivalent.
							method.instructions.insertBefore(encoding, new MethodInsnNode(Opcodes.INVOKESTATIC,
								BRIDGE, "mapWorldAnchorX", "(I)I", false));
							anchors++;
						}
					}
				}
			}
			if (anchors != 1)
			{
				throw new IllegalStateException("Expected one successful native world anchor, found " + anchors);
			}
			return;
		}
		String name;
		int xSlot;
		int ySlot;
		if (node.name.equals("xk"))
		{
			name = "al";
			xSlot = 0;
			ySlot = 1;
		}
		else if (node.name.equals("ei"))
		{
			name = "dt";
			xSlot = 2;
			ySlot = 3;
		}
		else
		{
			throw new IllegalArgumentException("Unexpected target class: " + node.name);
		}
		int matched = 0;
		for (MethodNode method : node.methods)
		{
			if (method.name.equals(name) && method.desc.equals("(III)V"))
			{
				InsnList entry = new InsnList();
				entry.add(new VarInsnNode(Opcodes.ILOAD, xSlot));
				entry.add(new VarInsnNode(Opcodes.ILOAD, ySlot));
				entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "mapSceneX", "(II)I", false));
				entry.add(new VarInsnNode(Opcodes.ISTORE, xSlot));
				method.instructions.insert(entry);
				matched++;
			}
		}
		if (matched != 1)
		{
			throw new IllegalStateException("Expected one native scene setter in " + node.name + ", found " + matched);
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 2)
		{
			throw new IllegalArgumentException("Usage: ScenePickingPatcher original-injected.jar patched-injected.jar");
		}
		Path source = Path.of(args[0]);
		Path output = Path.of(args[1]);
		StringBuilder actual = new StringBuilder();
		for (byte value : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))
		{
			actual.append(String.format("%02X", value & 255));
		}
		if (!SOURCE_SHA256.equals(actual.toString()))
		{
			throw new IllegalArgumentException("Unrecognized injected-client build; refusing native patch");
		}
		if (Files.exists(output))
		{
			throw new IllegalArgumentException("Output already exists; choose a fresh build path");
		}
		int patched = 0;
		try (ZipFile jar = new ZipFile(source.toFile()); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output)))
		{
			Enumeration<? extends ZipEntry> entries = jar.entries();
			while (entries.hasMoreElements())
			{
				ZipEntry entry = entries.nextElement();
				byte[] bytes = jar.getInputStream(entry).readAllBytes();
				if (entry.getName().equals("client.class") || entry.getName().equals("xk.class") || entry.getName().equals("ei.class") || entry.getName().equals("ry.class"))
				{
					ClassNode node = new ClassNode();
					new ClassReader(bytes).accept(node, 0);
					patchClass(node);
					ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
					node.accept(writer);
					bytes = writer.toByteArray();
					patched++;
				}
				out.putNextEntry(new ZipEntry(entry.getName()));
				out.write(bytes);
				out.closeEntry();
			}
		}
		if (patched != 4)
		{
			throw new IOException("Incomplete native patch: " + patched + " classes");
		}
		System.out.println("Installed scene callbacks and capability in verified local test client.");
	}
}
