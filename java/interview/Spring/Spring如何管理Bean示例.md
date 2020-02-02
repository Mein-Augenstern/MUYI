看在前面
====

> * <a href="https://blog.csdn.net/l18848956739/article/details/80917853">Spring如何管理Bean</a>

常常听老师说容器，容器是什么？```Spring```中是如何体现的？一直有疑惑，这两天看了一下```Spring```管理```bean```的```Demo```，对于```Spring```中的容器有了简单的认识。我们知道，容器是一个空间的概念，一般理解为可盛放物体的地方。在```Spring```容器通常理解为```BeanFactory```或者```ApplicationContext```。我们知道```Spring```的```IOC```容器能够帮我们创建对象，对象交给```Spring```管理之后我们就不用手动去```new```对象。

BeanFactory与ApplicationContext的区别是什么？
====

```BeanFactory```采用了工厂设计模式，负责读取```bean```配置文档，管理```bean```的加载，实例化，维护```bean```之间的依赖关系，负责```bean```的声明周期。而```ApplicationContext```除了提供上述```BeanFactory```所能提供的功能之外，还提供了更完整的框架功能：国际化支持、aop、事务等。同时```BeanFactory```在解析配置文件时并不会初始化对象，只有在使用对象```getBean()```才会对该对象进行初始化，而```ApplicationContext```在解析配置文件时对配置文件中的所有对象都初始化了，```getBean()```方法只是获取对象的过程。

因此我们一般在使用的时候尽量使用```ApplicationContext```。

ApplicationContext是如何管理Bean呢？
====

下面这个Demo简单模仿了这个原理。

1. 建立一个类PersonServiceBean，并在xml文件中进行配置。

```java
public interface IPersonService {

	void save();

}


public class PersonServiceBean implements IPersonService {

	@Override
	public void save() {
		System.out.println("我是save()方法");
	}

}
```

```xml
<bean id="personService" class="cn.itcast.service.impl.PersonServiceBean"></bean>  
```

2. 建立类BeanDefinition，提供一个构造函数，将其作为每个bean的公共转型类。

```java
public class BeanDefinition {

	private String id;

	private String className;

	public BeanDefinition(String id, String className) {
		this.id = id;
		this.className = className;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

}
```

3. 建立容器类TgbApplicationContext。

```java
public class TgbClassPathXMLApplicationContext {

	private List<BeanDefinition> beanDefines = new ArrayList<BeanDefinition>();

	private Map<String, Object> sigletons = new HashMap<String, Object>();

	public TgbClassPathXMLApplicationContext(String filename) {
		this.readXML(filename);
		this.instanceBeans();
	}

	/**
	 * 完成bean的实例化
	 */
	private void instanceBeans() {
		for (BeanDefinition beanDefinition : beanDefines) {
			try {
				if (beanDefinition.getClassName() != null && !"".equals(beanDefinition.getClassName().trim())) {
					sigletons.put(beanDefinition.getId(), Class.forName(beanDefinition.getClassName()).newInstance());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 读取xml配置文件
	 * 
	 * @param filename
	 */
	private void readXML(String filename) {
		SAXReader saxReader = new SAXReader(); // 创建读取器
		Document document = null;
		try {
			URL xmlpath = this.getClass().getClassLoader().getResource(filename);
			document = saxReader.read(xmlpath);
			Map<String, String> nsMap = new HashMap<String, String>();
			nsMap.put("ns", "http://www.springframework.org/schema/beans"); // 加入命名空间
			XPath xsub = document.createXPath("//ns:beans/ns:bean"); // 创建beans/bean查询路径
			xsub.setNamespaceURIs(nsMap); // 设置命名空间
			List<Element> beans = xsub.selectNodes(document); // 获取文档下所有的bean节点
			for (Element element : beans) {
				String id = element.attributeValue("id"); // 获取id属性值
				String clazz = element.attributeValue("class"); // 获取class属性值
				BeanDefinition beanDefine = new BeanDefinition(id, clazz);
				beanDefines.add(beanDefine);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * 获取bean实例
	 * 
	 * @param beanName
	 * @return
	 */
	public Object getBean(String beanName) {
		return this.sigletons.get(beanName);
	}

}
```

该类中拥有一个```List<BeanDefinition>```泛型集合类以及一个```Map<String,Object>```集合。通过查看代码我们知道这个容器类所做的事情如下：

 - 读取配置文件bean.xml，并根据文件中bean的id,class属性实例化一个BeanDefinition，装入泛型集合中。
 
 - 通过循环+反射，将List<BeanDefinition>中的bean加入到Map<String,Object>中，这里用反射将bean中的className属性转换为一个实例化的bean对象加入到了Map中。
 
 - 提供一个对外的接口，通过传入参数获取该bean。
 
4. 下面就是通过容器类获取具体bean的代码了

```java
public class SpringTest {

	@Test
	public void instanceSpring() {
		TgbClassPathXMLApplicationContext ctx = new TgbClassPathXMLApplicationContext("beans.xml");
		IPersonService personService = (IPersonService) ctx.getBean("personService");
		personService.save();
	}

}
```
通过调用```save()```方法可以调到```PersonServiceBean```中去。

通过这样的```Demo```，可以清楚看到```Spring```容器做的事情。它在初始化的时候将配置文件中```bean```以及相对应关系的配置都加入到```ApplicationContext```,通过一系列的转换将这些```bean```实例化，```bean```被它进行了管理，所以```ApplicationContext```就扮演了一个容器的角色。
