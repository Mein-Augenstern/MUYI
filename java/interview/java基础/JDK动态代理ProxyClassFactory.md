一、一句话描述ProxyClassFactory
====

ProxyClassFactory是用来生产代理类对象的。

二、Proxy内部类ProxyClassFactory源码注释
====

```java
/**
 * 根据类装入器和接口数组生成、定义和返回代理类对象的工厂函数。
 */
/**
 * A factory function that generates, defines and returns the proxy class given
 * the ClassLoader and array of interfaces.
 */
private static final class ProxyClassFactory
	implements BiFunction<ClassLoader, Class<?>[], Class<?>>
{
	// prefix for all proxy class names
	private static final String proxyClassNamePrefix = "$Proxy";

	// next number to use for generation of unique proxy class names
	private static final AtomicLong nextUniqueNumber = new AtomicLong();

	@Override
	public Class<?> apply(ClassLoader loader, Class<?>[] interfaces) {

		/*
		 * 遍历接口数组，返回关联的类对象
		 */
		Map<Class<?>, Boolean> interfaceSet = new IdentityHashMap<>(interfaces.length);
		for (Class<?> intf : interfaces) {
			/*
			 * Verify that the class loader resolves the name of this
			 * interface to the same Class object.
			 */
			Class<?> interfaceClass = null;
			try {
				// 使用给定的类装入器，返回与给定字符串名接口相关联的类对象，第二个参数为false表示不进行实例化
				interfaceClass = Class.forName(intf.getName(), false, loader);
			} catch (ClassNotFoundException e) {
			}
			if (interfaceClass != intf) {
				throw new IllegalArgumentException(
					intf + " is not visible from class loader");
			}
			/*
			 * Verify that the Class object actually represents an
			 * interface.
			 */
			if (!interfaceClass.isInterface()) {
				throw new IllegalArgumentException(
					interfaceClass.getName() + " is not an interface");
			}
			/*
			 * Verify that this interface is not a duplicate.
			 */
			if (interfaceSet.put(interfaceClass, Boolean.TRUE) != null) {
				throw new IllegalArgumentException(
					"repeated interface: " + interfaceClass.getName());
			}
		}

		String proxyPkg = null;     // package to define proxy class in
		int accessFlags = Modifier.PUBLIC | Modifier.FINAL;

		/*
		 * 包名创建逻辑
		 */
		/*
		 * Record the package of a non-public proxy interface so that the
		 * proxy class will be defined in the same package.  Verify that
		 * all non-public proxy interfaces are in the same package.
		 */
		for (Class<?> intf : interfaces) {
			int flags = intf.getModifiers();
			if (!Modifier.isPublic(flags)) {
				accessFlags = Modifier.FINAL;
				String name = intf.getName();
				int n = name.lastIndexOf('.');
				String pkg = ((n == -1) ? "" : name.substring(0, n + 1));
				if (proxyPkg == null) {
					proxyPkg = pkg;
				} else if (!pkg.equals(proxyPkg)) {
					throw new IllegalArgumentException(
						"non-public interfaces from different packages");
				}
			}
		}

		if (proxyPkg == null) {
			// if no non-public proxy interfaces, use com.sun.proxy package
			proxyPkg = ReflectUtil.PROXY_PACKAGE + ".";
		}

		/*
		 * Choose a name for the proxy class to generate.
		 */
		long num = nextUniqueNumber.getAndIncrement();
		String proxyName = proxyPkg + proxyClassNamePrefix + num;

		/*
		 * 生成代理类字节码
		 */
		/*
		 * Generate the specified proxy class.
		 */ 
		byte[] proxyClassFile = ProxyGenerator.generateProxyClass(
			proxyName, interfaces, accessFlags);
		try {
			/*
			 * 加载字节码文件到JVM中
			 */
			return defineClass0(loader, proxyName,
								proxyClassFile, 0, proxyClassFile.length);
		} catch (ClassFormatError e) {
			/*
			 * A ClassFormatError here means that (barring bugs in the
			 * proxy class generation code) there was some other
			 * invalid aspect of the arguments supplied to the proxy
			 * class creation (such as virtual machine limitations
			 * exceeded).
			 */
			throw new IllegalArgumentException(e.toString());
		}
	}
}
```
