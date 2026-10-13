/** JVM verification only: no native class initialization, constructors or actions. */
public final class NativeClassVerifier
{
	public static void main(String[] args) throws Exception
	{
		for (String name : new String[]{"client", "cp", "gn", "xk", "ei", "ry"})
		{
			Class<?> type = Class.forName(name, false, NativeClassVerifier.class.getClassLoader());
			System.out.println("Verified " + name + " without initialization; methods=" + type.getDeclaredMethods().length);
		}
	}
}
