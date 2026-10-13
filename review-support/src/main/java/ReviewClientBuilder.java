import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Review-only build tool; never included in the plugin or upstream change. */
public final class ReviewClientBuilder
{
    public static void main(String[] args) throws Exception
    {
        Path original = Path.of(args[0]);
        Path shadow = Path.of(args[1]);
        Path output = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempDirectory(output.getParent(), "companion-");
        Path patched = temporary.resolve("injected.jar");
        Path packaged = temporary.resolve("client.jar");
        Set<String> targets = new HashSet<>(Arrays.asList("client.class", "xk.class", "ei.class", "ry.class"));
        String adapter = "net/runelite/client/callback/SceneMirrorNativeAdapter.class";
        try
        {
            ScenePickingPatcher.main(new String[]{original.toString(), patched.toString()});
            try (ZipFile source = new ZipFile(original.toFile());
                 ZipFile patch = new ZipFile(patched.toFile());
                 ZipFile bundle = new ZipFile(shadow.toFile()))
            {
                Enumeration<? extends ZipEntry> originals = source.entries();
                while (originals.hasMoreElements())
                {
                    ZipEntry entry = originals.nextElement();
                    if (!entry.getName().endsWith(".class") || entry.getName().equals("com/jagex/oldscape/pub/OAuthApi.class")) continue;
                    ZipEntry bundled = bundle.getEntry(entry.getName());
                    if (bundled == null || !Arrays.equals(source.getInputStream(entry).readAllBytes(), bundle.getInputStream(bundled).readAllBytes()))
                        throw new IllegalArgumentException("Different native build in client: " + entry.getName());
                }
                try (ZipOutputStream result = new ZipOutputStream(Files.newOutputStream(packaged)))
                {
                    Enumeration<? extends ZipEntry> entries = bundle.entries();
                    while (entries.hasMoreElements())
                    {
                        ZipEntry entry = entries.nextElement();
                        result.putNextEntry(new ZipEntry(entry.getName()));
                        ZipFile from = targets.contains(entry.getName()) ? patch : bundle;
                        try (InputStream input = from.getInputStream(from.getEntry(entry.getName()))) { input.transferTo(result); }
                        result.closeEntry();
                    }
                    result.putNextEntry(new ZipEntry(adapter));
                    try (InputStream input = ReviewClientBuilder.class.getResourceAsStream("/" + adapter)) { input.transferTo(result); }
                    result.closeEntry();
                }
            }
            Files.move(packaged, output, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Review client built: " + output.getFileName());
        }
        finally
        {
            Files.deleteIfExists(packaged);
            Files.deleteIfExists(patched);
            Files.deleteIfExists(temporary);
        }
    }
}
