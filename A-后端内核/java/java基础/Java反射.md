## 看在前面

* <a href="https://xiaochai.github.io/2019/11/14/java-generic/">对java泛型的理解</a>

* <a href="https://www.jianshu.com/p/6a8997560b05">通过javap命令分析java汇编指令</a>

* <a href="https://www.jianshu.com/p/8c6586880b2c">为什么Java的泛型要做类型擦除</a>

* <a href="https://xiaochai.github.io/2019/11/14/java-generic/">Java的类型擦除</a>

* <a href="http://www.pulpcode.cn/2017/12/30/why-java-generic-use-type-eraser/">Java为什么要用类型擦除实现泛型?</a>

* <a href="https://extremegtr.github.io/2016/07/11/Covariance-And-Contravariance-In-Java/">Java中的协变与逆变</a>

* <a href="https://stackoverflow.com/questions/8481301/covariance-invariance-and-contravariance-explained-in-plain-english">Covariance, Invariance and Contravariance explained in plain English?</a>

* <a href="https://stackoverflow.com/questions/4343202/difference-between-super-t-and-extends-t-in-java">Difference between <? super T> and <? extends T> in Java</a>

* <a href="https://extremegtr.github.io/2016/05/30/JavaSE-study-advanced-generics/#u901A_u914D_u7B26_uFF08Wildcard_uFF09">JavaSE学习笔记 - 泛型进阶</a>

* <a href="https://www.cnblogs.com/zhangchengzi/p/9723250.html">Java程序语言的后门-反射机制</a>

* <a href="https://www.jianshu.com/p/da21b3a59b47">Java中与泛型相关的接口术语定义</a>

## 一句话答案


Java 反射机制是在运行状态中，对于任意一个类，都能够获得这个类的所有属性和方法，对于任意一个对象都能够调用它的任意一个属性和方法。这种在运行时动态的获取信息以及动态调用对象的方法的功能称为 Java 的反射机制。

## 反射是什么?

反射是什么呢？其实是java程序语言的一种机制，我理解为是java的后门。在其他文章中给出的定义和解释都比较晦涩难懂，不如先看一下具体的代码，再去理解，这样就容易很多。

为了更好的理解反射机制，不得不提到类对象的概念，为了不与类和对象的概念搞混，我们就首先看一下类和对象的概念。相信你已经非常熟悉类和对象的概念了，那么我就简单的描述一下类与对象的概念：

* 类：一个或一组事物的抽象描述，例如狗是对狗狗这一组事物的抽象描述。

* 对象：具体的某一个事物，例如哈士奇等，也可以说是类的一个实例。

**那么类对象是什么呢**？

在java中一切皆对象。既然一切皆对象，当然类也是一种对象，那么类的对象类型是什么呢？是java.lang.Class，你也许在其他的地方见到过。不仅仅是有类对象，而且还有属性,方法和构造器都是对象，属性是```java.lang.reflect.Field```类的对象，方法是```java.lang.reflect.Method```类的对象，构造器是```java.lang.reflect.Constructor```类的对象。看来在java中确实是一切皆对象。

**那么类对象是从哪里来的，怎么创建的呢**？

我们都知道，想要得到一个类的对象，最基本的方法就是通过new关键字，去实例化一个对象。但类对象是一个特殊的对象，自然不能使用new关键字，翻看Class类的源码就可以证明：

```java
/*
 * 私有化构造方法，只有java 虚拟机才能创建类对象
 */
/*
 * Private constructor. Only the Java Virtual Machine creates Class objects.
 * This constructor is not used and prevents the default constructor being
 * generated.
 */
private Class(ClassLoader loader) {
	// Initialize final field for classLoader.  The initialization value of non-null
	// prevents future JIT optimizations from assuming this final field is null.
	classLoader = loader;
}
```

Class类中只有这一个私有的构造方法。其实类对象是java虚拟机（JVM）在加载class文件的时候自动在虚拟机内存中给我们创建的。

这里就涉及到了另外一个机制：**类加载机制**。

简单描述一下类加载机制：就是虚拟机将class文件加载到虚拟机内存中，最终形成可以被虚拟机直接使用的java类型。虚拟机会将class文件中的信息按照所需的存储格式放在方法区中，同时会在内存中（HotSpot是在堆内存中）实例化一个java.lang.Class类对象。

类加载机制是一个很复杂的过程，不是本篇文章的重点，就不展开来说。至少到这里我们已经知道了，类对象是由虚拟机创建的而且HotSpot虚拟机将类对象存放在堆内存中。

## Java中与泛型相关的接口 之 综述

来自：https://www.jianshu.com/p/d32997d33bf8

## Java中与泛型相关的接口 之 术语定义

来自：https://www.jianshu.com/p/d32997d33bf8

## Java中与泛型相关的接口 之 Type

来自：https://www.jianshu.com/p/e525e8a69ce3

## Type及其子接口的来历

来自：https://www.jianshu.com/p/4cbe2e46e707

**泛型出现之前**

没有泛型的时候，只有所谓的原始类型。此时，所有的原始类型都通过字节码文件类Class类进行抽象。Class类的一个具体对象就代表一个指定的原始类型。

GPT4.0重新描述这句话的含义为：在泛型出现之前，Java中所有的数据类型都是通过Class类来表示的。Class类是Java中的一个特殊类，它的对象可以代表任何一个Java类型，包括基本数据类型和引用数据类型。每一个Class对象都代表了一个具体的类型，比如int.class代表了int类型，String.class代表了String类型等。这些Class对象被称为原始类型（raw type），因为它们没有泛型信息。

**泛型出现之后**

泛型出现之后，扩充了数据类型。从只有原始类型扩充了参数化类型（ParameterizedType）、类型变量类型（TypeVariable）、泛型限定的参数化类型 (含通配符+通配符限定表达式)（WildcardType）、泛型数组类型（GenericArrayType）。

GPT4.0重新描述这句话的含义为：泛型的引入为Java的类型系统增加了更多的灵活性和表达能力。除了原始类型，也就是没有类型参数的类和接口（例如，List而不是List），泛型还引入了以下几种类型：
- 参数化类型（ParameterizedType）：这是使用泛型时最常见的情况，即类或接口后跟一对尖括号，里面包含一个或多个类型参数，例如List或Map<Integer, String>。
	- 就好比是给一个盒子加上标签，说明这个盒子里应该放什么东西。比如，你有一个装苹果的盒子，你在盒子上贴上一个标签写着“苹果”，这样就明确了这个盒子里只能放苹果。在Java中，这就像是你声明了一个List，这个List里只能放字符串。
	```java
	List<String> stringList = new ArrayList<String>();
	Map<Integer, String> map = new HashMap<Integer, String>();
	```
 
- 类型变量类型（TypeVariable）：这通常是在定义泛型类或泛型方法时出现的，它是代表一个类型参数的变量，例如在类定义中的T，如class MyClass中的T。
	- 这就像是给盒子定义了一个名字，让你可以在其他地方引用这个盒子。比如定义一个盒子叫做T，然后无论你需要什么类型的盒子，你都用T来代替，直到你明确告诉程序，T是苹果盒子还是香蕉盒子。
	```java
	// 在类定义中
	public class GenericClass<T> {
	    private T value;
	    
	    public T getValue() {
	        return value;
	    }
	    
	    public void setValue(T value) {
	        this.value = value;
	    }
	}
	
	// 在方法定义中
	public <T> T genericMethod(T param) {
	    return param;
	}
	```
   
- 泛型限定的参数化类型（WildcardType）：这涉及到使用通配符（?）作为类型参数，它可以进一步限定为上界（extends）或下界（super），表示该泛型类型被限定为某个特定类型的子类型或父类型。例如List<? extends Number>表示这个列表包含的类型是Number或其子类型。
	- 这一点就像是对盒子的内容做了一些额外的说明，比如你可以说这个盒子里可以放苹果，也可以放苹果的任何品种（如青苹果、红苹果等），或者说这个盒子可以放水果，但必须是类似苹果这样的水果。在Java中，这就像是使用了通配符，比如List<? extends Fruit>，意思是这个List可以放Fruit或者Fruit的任何子类，如苹果或香蕉。
	```java
	List<? extends Number> numbers = new ArrayList<Integer>(); // 上界通配符，可以存放Number及其子类
	List<? super Integer> integers = new ArrayList<Number>(); // 下界通配符，可以存放Integer及其父类
	```
 
- 泛型数组类型（GenericArrayType）：这表示元素类型为某个参数化类型或类型变量的数组，例如List[]是一个数组，其元素类型为List。
	- 这就好比你有很多个相同类型的盒子，现在你想把这些盒子放在一个大箱子里。在Java中，这就像声明了一个List[]，意思是你有一个数组，每一个元素都是可以放字符串的List。	
	```java
	// Java不允许直接创建泛型数组，但可以这样声明
	List<String>[] arrayOfStringLists;
	
	// 创建泛型数组需要通过Array.newInstance
	arrayOfStringLists = (List<String>[]) Array.newInstance(ArrayList.class, 10);
	```

总之，泛型的出现让Java的类型系统更加丰富和复杂，使得程序员可以创建更加通用和类型安全的代码。其中关于不变性详见文档的描述：https://xiaochai.github.io/2019/11/14/java-generic/

**与泛型有关的类型不能和原始类型统一到Class的原因** 

产生泛型擦除的原因：为了使用泛型的优势又不真正引入泛型，Java采用泛型擦除的机制来引入泛型。Java中的泛型仅仅是给编译器javac使用的，确保数据的安全性和免去强制类型转换的麻烦。但是，一旦编译完成，所有的和泛型有关的类型全部擦除。

Class不能表达与泛型有关的类型：因此，与泛型有关的泛型实例（ParameterizedType）、类型变量（TypeVariable）、泛型参数表达式 (含通配符+通配符限定表达式)（WildcardType）、泛型数组（GenericArrayType）这些类型全部被打回原形，在字节码文件中全部都是泛型被擦除后的原始类型，并不存在和自身类型一致的字节码文件。所以和泛型相关的新扩充进来的类型不能被统一到Class类中。

与泛型有关的类型在Java中的表示：为了通过反射操作这些类型以迎合实际开发的需要，Java就新增了ParameterizedType，GenericArrayType，TypeVariable 和WildcardType几种类型来代表不能被归一到Class类中的类型但是又和原始类型齐名的类型。

Type的引入：统一与泛型有关的类型和原始类型Class：为了程序的扩展性，最终引入了Type接口作为Class，ParameterizedType，GenericArrayType，ypeVariable和WildcardType这几种类型的总的父接口。这样实现了Type类型参数可以接受以上五种子类的实参，而以上五种类型的返回值可以用Type类型的变量来接收。

从上面看到，Type的出现仅仅起到了通过多态来达到程序扩展性提高的作用，没有其他的作用。因此Type接口的源码中没有任何方法。

## ParameterizedType

可阅读：https://www.jianshu.com/p/cfa74c980b25

直观体感参照：https://www.cnblogs.com/Meiwah/p/10434893.html

第一种说法

> ParameterizedType就是参数化类型的意思，声明类型中带有“<>”的都是参数化类型，比如List<Integer>，Map<String,Long>

```java
getActualTypeArguments()返回Type[]，即“<>”里的参数，比如Map<String,Integer>

getRawType()返回Tpye，得到“<>”前面的类型，比如List<String>

getOwnerType()返回Type，O<T>.I<S>类型变量调用时会返回O<T>，比如Map.Entry<Long,Short>
```

第二种说法

> 这是在 java.lang.reflect 包中一个接口的名称，很多文章中把它翻译为“参数化类型”，我通过参阅多方资料发现其实这个接口就是对“泛型实例”的说明。所以在之后的文章中，我就把这个接口称之为“泛型实例”。
其实这个接口就是在说明一个带参数的类型，如：Collection<String>，Map<String,Object>等。这看似大家平常所说的泛型，但其实不然。我们大家平时所说的泛型是Collection<E>和Map<K,V>等，而ParameterizedType表示的是泛型（Collection<E>和Map<K,V>等）的一种实例（Collection<String>和Map<String,Object>）。


## TypeVariable

可阅读：https://www.jianshu.com/p/89a64318e290

## GenericDeclaration

可阅读：[https://www.jianshu.com/p/89a64318e290](https://www.jianshu.com/p/a51bd9f41c21)

## GenericArrayType

可阅读：https://www.jianshu.com/p/f8c26eab7b68

## WildcardType

可阅读：https://www.jianshu.com/p/03ea6d05204b

## 反射常用方法

### getGenericSuperclass()


先看一段代码

```JAVA

@SuppressWarnings({ "unchecked", "deprecation" })
private void init() {
	Class<T> entityClass = null;
	Class<?> c = getClass();
	Type type = c.getGenericSuperclass();

	if (type instanceof ParameterizedType) {
		Type[] parameterizedType = ((ParameterizedType) type)
				.getActualTypeArguments();
		entityClass = (Class<T>) parameterizedType[0];
	}

	// 进行扫注解，确定模板的输入输出文件名
	DataTemplate dataTemplate = entityClass
			.getAnnotation(DataTemplate.class);
	if (null != dataTemplate) {
		this.outputName = dataTemplate.output();
		if (NullOrEmpty.isNullOrEmpty(dataTemplate.template())) {
			this.templateName = entityClass.getName();
		} else {
			this.templateName = dataTemplate.template();
		}
	} else {
		this.outputName = entityClass.getName();
		this.templateName = entityClass.getName();
	}

	this.freeMarkerCfg = new Configuration();
	freeMarkerCfg.setObjectWrapper(new DefaultObjectWrapper());
	freeMarkerCfg.setDefaultEncoding(Charset.forName("UTF-8").name());

}
```

```JAVA
public class Person<T> {
 
}
```

```java

import java.lang.reflect.*;
 
public class Student  extends Person<Student>{
 
	@SuppressWarnings("unchecked")
	public static <T> void main(String[] args) {
		 Student st=new Student();
		 @SuppressWarnings("rawtypes")
		 Class clazz=st.getClass();
		 //getSuperclass()获得该类的父类
		 System.out.println(clazz.getSuperclass());
		 //getGenericSuperclass()获得带有泛型的父类
		 //Type是 Java 编程语言中所有类型的公共高级接口。它们包括原始类型、参数化类型、数组类型、类型变量和基本类型。
		 Type type=clazz.getGenericSuperclass();
		 System.out.println(type);
		 //ParameterizedType参数化类型，即泛型
		 ParameterizedType p=(ParameterizedType)type;
		 //getActualTypeArguments获取参数化类型的数组，泛型可能有多个
		 Class<T> c=(Class<T>) p.getActualTypeArguments()[0];
		 System.out.println(c.getName());
		 System.out.println(c);
	}
 
}
```
执行结果:

```java
class com.neteae.web.test.Person
com.neteae.web.test.Person<com.neteae.web.test.Student>
com.neteae.web.test.Student
class com.neteae.web.test.Student
```

## Java中产生泛型擦除的原因是什么呢？

**GPT4.0的第一次回答**

Java中的泛型擦除是指在编译时期移除所有的泛型信息，确保代码能与Java早期版本的代码兼容。泛型在Java 5中引入，为了兼容以前的版本，Java的泛型实现采用了擦除型的方式，也就是说在编译阶段，泛型类型被擦除，转换为原始类型（raw type），同时插入类型转换代码来保证类型安全。

泛型擦除的主要原因有：

- 向后兼容性：Java是一种历史悠久的语言，当引入泛型时，已经有大量的Java代码库和应用在运行。保持向后兼容性意味着即使不修改这些已有代码，也可以与使用了泛型的新代码互操作。

- 平滑过渡：泛型擦除允许开发者逐步地采用泛型，而不是强制一次性全面更新。他们可以保留原有的代码，同时在新代码中使用泛型。

- 避免性能开销：因为泛型信息只在编译时检查，无需在运行时保持，因此可以避免在运行时进行泛型相关的类型检查所可能带来的性能开销。

有了泛型擦除，泛型只在编译阶段存在，一旦代码被编译，所有的泛型类型参数都会被擦除，替换为他们的原始类型（比如Object）或者是它们的边界（bounded types），然后在需要的地方插入类型转换代码来保证类型安全。

这个决定是Java语言设计者为了兼容性和平滑过渡而做出的权衡，尽管它导致了一些泛型使用上的限制，例如不能使用基本数据类型作为泛型类型参数，不能在运行时获取泛型的类型信息等。

**GPT4.0的第二次回答**

在Java中，泛型擦除的原因主要是为了让新版本的Java代码能和旧版本的代码兼容。想象一下，如果有很多年前就写好的Java代码，那时候还没有泛型这个概念。后来Java引入了泛型，如果新的Java版本要求所有代码都必须使用泛型，那么所有老代码都需要重写，这将是一个巨大的工作量，并且会造成很多问题。

因此，Java设计者决定让编译器在编译代码的时候将泛型相关的信息"擦除"，这样编译后的代码就像是没有使用泛型一样。这意味着不论你的代码里泛型是怎样写的，最终运行的时候，泛型都被转换为了Java原来的类型（比如Object）。这样做的好处是，即使是新的代码使用了泛型，它们编译后也能和旧的Java代码无缝工作，不会因为引入了泛型而产生冲突。

举个例子，你有一个泛型List，在你的代码中，你可以放心地往里面添加整数，并且取出来的时候也清楚地知道是整数类型。但是在编译后的代码中，Java会将这个List视为简单的List，并且在需要的时候，编译器会自动帮你插入代码来转换类型，确保你取出来的仍然是Integer。

这种方式的缺点是，它限制了泛型的某些用法。例如，你无法在运行时知道一个集合的元素类型是什么，因为所有的泛型信息在运行时都已经被擦除了。

简而言之，泛型擦除是Java为了保持代码的兼容性和避免重写大量旧代码所做的设计决策。

**GPT4.0关于你无法在运行时知道一个集合的元素类型是什么，因为所有的泛型信息在运行时都已经被擦除了。 这句话是什么意思？比如List<String>类型的对象，get方法是知道返回的类型是String的**

对不起，可能我之前的表述有所不清。我试着更清楚地解释一下：

确实，当你在代码中使用 List<String> 并在之后调用 get 方法时，你拿到的是一个 String 类型的对象。这是在编译时期保证的，因为编译器会检查并确保你只把 String 加入到列表中，并且在使用 get 方法时，它会自动把返回的对象视为 String 类型。

问题出现在如果你想要在运行时检查列表中的元素类型。由于泛型信息在运行时被擦除，列表只是一个普通的 List，并不记得它的元素是 String 类型的。这是因为Java中的泛型是通过类型擦除来实现的，这意味着在编译后的Java字节码中，所有泛型类型的信息都被替换掉了，换成了它们的原始类型（大多数情况下是 Object 类型）。

例如，以下代码段在运行时将不能确定 list 的元素类型：

```java
List<String> list = new ArrayList<>();
list.add("hello");

// 这在编译时是String类型，运行时类型信息丢失
Class<?> clazz = list.getClass();
System.out.println(clazz); // 输出 java.util.ArrayList，而不是带泛型的类型

// 以下尝试获取泛型类型的信息将失败，因为运行时类型信息已丢失
Type genericSuperclass = clazz.getGenericSuperclass();
System.out.println(genericSuperclass); // 输出java.util.AbstractList<E>，E是类型变量，具体类型擦除了
```

在运行时，你只能确定 list 是 List 类型的对象，但不能确定它是 List<String>、List<Integer> 还是其他泛型类型，除非你通过其他方式来记住或检查这些信息（如使用自定义的数据结构）。

因此，当说到"无法在运行时知道一个集合的元素类型是什么"，它指的是类型参数（例如 String 在 List<String> 中）的具体信息在运行时不可用，而不是说你不能在代码中使用泛型确保类型安全。

## 有什么其他的方法可以解决反型信息被擦除的问题么？

**GPT4.0的第一次回答**

尽管Java中的泛型类型信息在运行时会被擦除，但是还是有一些方法可以在一定程度上“绕过”泛型擦除，获取到泛型类型信息。这些方法包括：

1、使用反射的特殊场景:
如果一个类继承自具有泛型参数的父类，那么这些泛型参数的实际类型信息会保留在子类的 Class 对象中。

```java
class StringList extends ArrayList<String> { }
```

在这种情况下，你可以通过反射来获取 StringList 的超类的泛型类型。

```java
Type genericSuperclass = StringList.class.getGenericSuperclass();
if(genericSuperclass instanceof ParameterizedType) {
    ParameterizedType type = (ParameterizedType) genericSuperclass;
    Type actualTypeArgument = type.getActualTypeArguments()[0];
    System.out.println(actualTypeArgument); // 输出 java.lang.String 类型
}
```

2、使用类型标记（Type Token）:

你可以定义一个类，它持有一个关于泛型参数的Class对象，就像是一个类型标记。比如：

```java
class TypeRef<T> {
    final Class<T> type;
    public TypeRef(Class<T> type) { this.type = type; }
}

TypeRef<String> typeRef = new TypeRef<String>(String.class) {};
Class<?> clazz = typeRef.type;
System.out.println(clazz); // 输出 java.lang.String
```

3、通过子类化:

使用匿名内部类持有具体的泛型类型信息。

```java
abstract class TypeReference<T> {
    Type type;
    protected TypeReference() {
        ParameterizedType parameterizedType = 
            (ParameterizedType) getClass().getGenericSuperclass();
        type = parameterizedType.getActualTypeArguments()[0];
    }
    public Type getType() {
        return this.type;
    }
}

TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {};
System.out.println(typeRef.getType()); // 输出 java.util.List<java.lang.String>
```

4、使用第三方库:

一些第三方库，如Google的Guava库中的 TypeToken 类，提供了一种方式来保持泛型类型的信息。

```java
TypeToken<List<String>> typeToken = new TypeToken<List<String>>() {};
Type type = typeToken.getType();
System.out.println(type); // 输出 java.util.List<java.lang.String>
```

使用这些方法可以帮助你在某些特定场景下绕过泛型擦除的限制。然而，它们都有自己的局限性，应该根据具体情况和需求来使用。

## 与泛型有关的类型不能和原始类型统一到Class的原因有哪些?

**GPT4.0第一次回答**

泛型在Java中是通过类型擦除来实现的，这意味着泛型类型参数在编译时会被擦除并替换为它们的擦除类型（通常是Object）。这项设计使得运行时的Java类型系统不具备对泛型的直接支持。因此，泛型类型（如 List<String> 和 List<Integer>）在运行时都被视为它们的原始类型（如 List）。这导致了无法直接将泛型类型统一到Class对象的几个原因：

1、类型擦除：由于Java的泛型信息在运行时会被擦除，所有泛型类型实例在运行时都会变成它们的原始类型。例如，List<String> 和 List<Integer> 在运行时都会被视为简单的 List。因此，泛型类型无法保留足够的信息来表示其具体的泛型参数类型。

2、类型信息丢失：因为泛型信息不保留在运行时，所以无法在运行时区分 List<String> 和 List<Integer> 这样的类型。它们的 Class 对象都是 List.class。

3、泛型的不变性：泛型类型在编译时是不变的，这意味着 List<String> 不是 List<Object> 的子类型。这种不变性在运行时无法通过Class对象表达，因为所有的泛型类型都退化为了它们的原始类型。
  
4、运行时类型信息的限制：Java的反射API只提供了有限的泛型信息。虽然可以通过反射获取某些泛型类型的信息（比如字段、方法的返回类型等），但这些信息并不属于实例层面，而是属于声明层面。实例本身并不携带这些泛型参数信息。

5、兼容性考虑：Java泛型的设计初衷是保持向后兼容。为了保证新旧代码能够无缝工作，不能改变基本的 Class 类的行为，否则会破坏现有的基于Class对象的代码。

所以，在Java中，Class对象代表的是运行时类型，而不是编译时类型。运行时类型与泛型擦除后的类型一致，而编译时类型包括了泛型的类型参数。这是为了确保泛型的引入不会影响Java平台的稳定性和现有代码的有效性。

## 关于泛型的不变性GPT4.0回答补充

泛型的不变性是指在Java中，具有不同泛型参数的相同泛型类型之间没有继承关系。这可能与数组的行为不同，因为数组是协变的。比如，String[] 是 Object[] 的子类型，但是 List<String> 不是 List<Object> 的子类型。这里是一个具体的例子来解释泛型的不变性：
```java
List<String> stringList = new ArrayList<String>();
List<Object> objectList;
// objectList = stringList; // 编译错误：类型不兼容
```
尽管 String 是 Object 的子类型，但是 List<String> 并不是 List<Object> 的子类型。如果Java允许这种赋值，那么我们将能够向 objectList 中添加任何类型的对象，这将破坏 stringList 的类型安全性，因为实际上它只能包含字符串。
在编译时，Java的泛型系统会保证类型安全，不允许这种类型的赋值。但是在运行时，由于类型信息已经被擦除，泛型的不变性不再是问题，因为泛型类型参数已经不存在了。所有的泛型类型都退化成了它们的原始类型，这意味着运行时系统只知道 stringList 和 objectList 都是 List 的某种实例，但是不知道它们具体的泛型参数是什么。
对于Class对象来说，这意味着：
```java
Class<List> listClass = List.class;
Class<List<String>> stringListClass = List.class;
Class<List<Object>> objectListClass = List.class;

// 运行时这三个Class对象都是相等的，因为泛型参数被擦除了。
System.out.println(listClass == stringListClass); // true
System.out.println(listClass == objectListClass); // true
```
所有的泛型List在运行时都是同一个Class对象（即List.class），不论它们的泛型参数是什么。这就是为什么泛型的不变性在运行时无法通过Class对象表达。由于类型擦除，泛型参数信息在运行时不可用，所以Class对象无法区分具有不同泛型参数的相同类型。

其中关于**泛型的不变性是指在Java中，具有不同泛型参数的相同泛型类型之间没有继承关系。GPT4.0的第二次回答**

泛型的不变性指的是，在Java中，即使两个泛型类型有相同的原始类型（比如都是List），只要它们的泛型参数不一样（比如一个是String，另一个是Integer），它们就被认为是完全不同的类型，没有继承关系或子类型关系。

举例来说，尽管String是Object的子类型，但是List<String>并不是List<Object>的子类型。下面的代码就不能通过编译：

```java
// 不能通过编译，因为List<String>不是List<Object>的子类型
List<String> stringList = new ArrayList<String>();
List<Object> objectList = stringList;  // 编译错误
```

这就是所谓的“不变性”——即你不能将一个泛型类型的对象赋值给另一个泛型类型的引用，即使它们的泛型参数之间存在继承关系。

这个特性是Java泛型系统设计的一部分，主要目的是为了保证类型安全。如果Java允许List<String>被赋值给List<Object>，那么我们就可以往List<Object>中添加任意类型的元素，这会破坏原本只能包含String的List<String>的类型安全性。

不过，Java提供了泛型通配符和边界来增加泛型的灵活性，通过这种方式，你可以声明某个泛型类型是另一个泛型类型的子类型。例如：

```java
// 使用通配符和上限边界来表示List<String>是List<? extends Object>的子类型
List<String> stringList = new ArrayList<String>();
List<? extends Object> objects = stringList; // 这样是可以的
```

在这个例子中，`List<? extends Object>` 表示“某个未知类型的List，这个未知类型是Object的子类型”。因为String是Object的子类型，所以List<String>可以赋值给List<? extends Object>。注意，这里使用的是通配符`?`，这是另一种类型的表达，它不是说List<String>是List<Object>的子类型，而是说List<String>可以被当作List<? extends Object>的一个实例。

## 如果继承了某个泛型类，注意至少需要定义同样的边界或者更多的边界限制。代码实例-来自GPT4.0的第一次回答。

在Java中，当你创建一个新的类继承自一个泛型类时，你需要提供类型参数。对于这些类型参数，你可以使用具体的类型，或者继续使用泛型参数，但是需要遵守原有的泛型边界限制，或者定义更严格的边界。

泛型边界限制通常使用 `extends` 关键字来定义，它指定了一个类型参数必须是某个特定类的子类型，或者是某个接口的实现。

让我们看一个例子来理解这个概念：

```java
// 假设有一个泛型类，它有一个边界限制
// 这里T必须是Number的子类
class GenericClass<T extends Number> { 
    private T data;
    public T getData() {
        return data;
    }
}

// 现在我们创建一个新类继承自GenericClass
// 这里我们用一个具体的类型Integer替代了泛型参数T
// Integer是Number的子类，满足边界限制
class IntegerClass extends GenericClass<Integer> {
    // ...
}
```

在上面的例子中，`GenericClass` 是一个泛型类，它的类型参数 `T` 必须是 `Number` 的子类。当创建 `IntegerClass` 继承自 `GenericClass` 时，我们使用了 `Integer` 作为类型参数，它满足了 `Number` 的子类这个要求。

现在，假如你想继续使用泛型参数，但要保持或增加边界限制：

```java
// 这个类继承自GenericClass，但它仍然是一个泛型类
// 我们在这里增加了一个额外的边界限制：E必须是Comparable<E>的实现
class ComparableGenericClass<E extends Number & Comparable<E>> extends GenericClass<E> {
    // ...
}
```

在这个例子中，`ComparableGenericClass` 仍然是一个泛型类，它的泛型参数 `E` 必须遵守两个条件：它必须是 `Number` 的子类（从 `GenericClass` 继承的边界），并且它必须实现了 `Comparable<E>` 接口。这里我们没有放宽边界，而是增加了一个额外的边界限制。

总结一下，当你继承一个泛型类时：

1. 如果使用具体的类型替代原泛型类的类型参数，那么这个类型必须至少满足原始泛型类的边界限制。
2. 如果继续使用泛型参数，你可以保持原有的边界限制，或者增加更多的限制，但不能放宽这些限制。

## Java反型中如果使用了上界通配符之后，就无法对其赋值，那这种情况如何对其赋值呢？代码实例-来自GPT4.0的第一次回答。

在Java中，使用了上界通配符（`? extends Type`）的泛型类型，确实是无法进行赋值的，因为编译器无法保证类型安全。比如，`List<? extends Number>` 可以引用一个 `List<Integer>` 或 `List<Double>`，但是你不能向它添加任何 `Number`，因为你无法确定 `List` 的具体类型是什么。

但是，你可以读取其中的元素，因为你能确信无论 `List` 的具体类型是什么，它至少包含的是 `Number` 对象。

如果你需要向使用了上界通配符的集合中添加数据，你会需要其他方式来处理。以下是几种可能的方法：

1. **不使用通配符**：如果你需要向集合中添加数据，那么最直接的方式是不使用上界通配符。如果你知道集合将处理 `Integer` 类型的数据，那么直接使用 `List<Integer>`。

2. **确定类型**：如果你确实需要使用通配符，但又需要赋值，你可以创建一个泛型方法，该方法有一个类型参数，这个参数可以用来确定集合的具体类型。

   ```java
   public <T extends Number> void addNumber(List<T> list, T number) {
       list.add(number);
   }

   List<Integer> ints = new ArrayList<>();
   addNumber(ints, Integer.valueOf(42));
   ```

3. **受限的方法**：如果你需要为某些特定的类型提供赋值的功能，你可以创建专门的方法来处理这些类型。

   ```java
   public void addNumberToList(List<? super Integer> list) {
       list.add(Integer.valueOf(42)); // 有效，因为我们知道List至少可以接受Integer
   }

   List<Number> nums = new ArrayList<>();
   addNumberToList(nums);
   ```

4. **使用下界通配符** (`? super Type`)：下界通配符允许你向集合中添加具体的类型，因为你可以确保集合至少能接受这个类型。

   ```java
   List<? super Integer> intList = new ArrayList<>();
   intList.add(Integer.valueOf(10)); // 有效
   ```

在这个例子中，由于使用了下界通配符，我们可以向 `intList` 中添加 `Integer`，因为我们知道无论 `intList` 的具体类型是 `Integer` 的哪个超类，添加 `Integer` 都是安全的。

记住，通配符的使用是为了提高代码的灵活性，但它也带来了限制。通常，上界通配符（`? extends Type`）适合于只从集合中读取的情况（“生产者”），而下界通配符（`? super Type`）适合于只向集合中写入的情况（“消费者”）。这是所谓的PECS（Producer-Extends, Consumer-Super）原则。

下面是一个使用上界通配符的Java代码示例。在这个例子中，我们定义了一个方法，它可以读取任何 `Number` 类型或其子类型元素的列表，并计算这些元素的总和：

```java
import java.util.Arrays;
import java.util.List;

public class UpperBoundedWildcardExample {

    // 使用上界通配符来定义一个只能读取Number或其子类型元素的列表
    public static double sumOfList(List<? extends Number> list) {
        double sum = 0.0;
        for (Number n : list) {
            sum += n.doubleValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        // Integer列表是Number的子类，可以传递给sumOfList方法
        List<Integer> integerList = Arrays.asList(1, 2, 3, 4, 5);
        System.out.println("Sum of integerList: " + sumOfList(integerList));

        // Double列表也是Number的子类，同样可以传递给sumOfList方法
        List<Double> doubleList = Arrays.asList(1.5, 2.5, 3.5);
        System.out.println("Sum of doubleList: " + sumOfList(doubleList));
    }
}
```

在这个例子中，`sumOfList` 方法的参数是一个带有上界通配符的 `List`，这意味着这个 `List` 可以是任何 `Number` 类型或其子类型的 `List`（例如 `List<Integer>`、`List<Double>` 等）。我们通过遍历列表并将每个元素累加到 `sum` 中来计算总和。

由于我们不打算向列表中添加任何元素，仅仅是读取列表中的元素，所以使用上界通配符是合适的，这样的设计使 `sumOfList` 方法更加灵活，可以接受多种不同的数字类型的列表作为参数。

# 以下内容来自链接：https://xiaochai.github.io/2019/11/14/java-generic/

## 协变（covariance）、逆变（contravariance）、不变（invariance）

协变、逆变、不变这些表示的是类型转化后的关系。

定义 f(t) 表示某种类型转换，初始类型为t；A ≤ B 表示A是比B更加派生/特定（more derived type/more specific）的类型；一般理解为A继承自B，或者可以将B类型赋值给A。

当 A ≤ B 时：

如果 f(A) ≤ f(B)，则类型转换f是协变的，或称之为具有协变性；

如果 f(B) ≤ f(A)，则类型转换f是逆变的，或称之为具有逆变性；

如果既不是 f(A) ≤ f(B) 也不是 f(B) ≤ f(A)，则类型转换f是不可变的，或称之为具有不变性。

### 数组的协变性

对于Java的数组，可以将Integer[]赋值给Number[]，这说明了Java的数组具有协变性质。

但这引起了一些问题，如下代码：

```java
Number n[] = new Integer[2];
n[0] = 10;
n[1] = 11.2; // 编译期没有问题，但在运行时会出错，ArrayStoreException异常
```

### 重写返回值的协变性

Java中另外一个具有协变性质的是重写(override)函数的返回值。从Java5开始，重写函数支持协变返回值，即可以返回超类方法指定类型的子类型：

```java
class Base1{
    public Number f(){ return 3;}
}
class Derive1 extends Base1{
    @Override
    public Integer f(){return 4;}
}
```

### 泛型的不变性与通配符

对于Java的泛型，其本身是不变性的，所以以下语句不能编译通过List<Number> l = new List<Integer>();。

为了解决此问题，引入了通配置符(?)和上界通配符(? extends)以及下界通配符(? super)。

看以下的例子：

```java
class Food{}
class Meat extends Food{}
class Fruit extends Food{}
class Apple extends Fruit {}
class Banana extends Fruit {}
public class TypeTransform {
    public static void main(String[] args){
        ArrayList<? extends Fruit> fruits = new ArrayList<Apple>();
        // 以下代码都无法通过编译，使用了上界通配符之后，就无法对其赋值，但可以正常取出
        //fruits.add(new Fruit());
        //fruits.add(new Apple());
        //fruits.add(new Food());
        Fruit f = fruits.get(0);

        ArrayList<? super Fruit> fruits2 = new ArrayList<Food>();
        fruits2.add(new Fruit());
        fruits2.add(new Apple());
        // 任何Fruit的超类List都可以赋值到fruits2，所以fruits2里的元素只能是Fruit或者其子类才能满足这个要求
        // 以下句子无法通过编译，因为如果fruits2的值是ArrayList<Fruit>，此时的Food无法转型成Fruit
        // fruits2.add(new Food());
        // 以下代码无法通过编译，使用了下界通配符之后，无法确定获取的返回值，因为fruits的元素可能是任何Fruit的超类对象
        // Fruit f2 = fruits2.get(0);
        // 毕竟，可以把任何对象赋值给Object
        Object f3 = fruits2.get(0);
    }
}
```

如何理解以上这个例子呢？我们一个一个看。

首先我们来看一下fruits这个变量，他是一个ArrayList<? extends Fruit>，这使得fruits能够接收任何Fruit子类的ArrayList，例如ArrayList<Apple>, ArrayList<Banana>等等。

既然这样，编译器无法确定是这之中的哪一种，自然也无法将Apple或者Banana这些类型添加到ArrayList。

表现出现就是任何对象都无法被add进去。但取出来的结果可以确定其至少是一个Fruit。

与此相反，fruits2使用ArrayList<? super Fruit>实现了泛型的逆变，即它可以接收Fruit本身以及其超类的ArrayList，例如ArrayList, Array。

编译器无法确认是这些中的哪一个，所以在取出时无法确认会是哪一个基类的容器，只能确认是一个Object，因为Object是所有类的基类。但是任何Fruit的子类都可以向上转型成Fruit的基类，所以对其进行赋值是安全的。

### 无界通配符

无界通配符<*>意味着任一类型。例如ArrayList<*>表示存储任何类型的ArrayList容器，看起来与原生类型ArrayList类似，也与ArrayList<Object>类似，我们来看看他们之间的区别：

```java
ArrayList<?> list = new ArrayList<Fruit>();
// 因为无法确认list的边界，所以以下4个语句无法编译通过
// list.add(new Food());
// list.add(new Fruit());
// list.add(new Apple());
// Food f1 = list.get(0);
Object o1 = list.get(0);


ArrayList list2 = new ArrayList<Fruit>();
list2.add(new Food());
list2.add(new Fruit());
list2.add(new Meat());
Object o2 = list2.get(0);

// 泛型的不变性，以下语句无法编译通过
// ArrayList<Object> list3 = new ArrayList<Fruit>();
```
对于list，编译器无法知道这个ArrayList真实是用来存储哪一种类型的，所以任何类型都不能安全的加入到这个容器中，表现为所有的add都编译不通过。获取时也一样，只能确认它是一个Object。

list2原生类型的写法，其实就回归到泛型之前的写法了，会得到一个unchecked的warning，可以使用@SuppressWarnings("unchecked")抑制警告。任何类型都可以赋值给它，虽然他实际是一个Fruit，但在被赋值给list2时，这个Fruit信息也已经丢失了。

由于泛型的不变性，list3不会赋值成功。

### 何时使用extends和super

从上面的说明可以看出使用extends的泛型容器，无法向里添加元素；而使用super的泛型容器可以向里添加元素，但无法取出确切的类型。

以容器的角度看，从容器里取东西，这时候容器是生产者；往容器里添加东西，这时候容器是消费者，这就导出了PECS原则（Producer extends Consumer super）。

即：

如果只需要从集合中获得类型T , 使用<? extends T>通配符

如果只需要将类型T放到集合中, 使用<? super T>通配符

如果既要获取又要放置元素，则不使用任何通配符。例如List<Apple>。

看两个典型的例子：

```java
public class Pecs {
    // Comparable需要用super的理由是如果实现Comparable的不一定是本类，也有可能是基类，这个? super T涵盖了这种情况
    // 如果直接使用Comparable<T>，并且Collection没有使用extends的话，则max(apples)将会编译失败
    // 参数Collections添加? extends T，是为了在函数中不修改这个集合
    public static <T extends Comparable<? super T>> T max(Collection<? extends T> c) {
        if (c.isEmpty()) {
            return null;
        }
        Iterator<? extends T> iterator = c.iterator();
        T max = iterator.next();
        while (iterator.hasNext()) {
            T t = iterator.next();
            if (t.compareTo(max) > 0) {
                max = t;
            }
        }
        return max;
    }

    // src只读，dest只写
    public static <T> void copy(ArrayList<? extends T> src, ArrayList<? super T> dest) {
        for (int i = 0; i < src.size(); i++) {
            dest.set(i, src.get(i));
        }
    }

    public static void main() {
        List<Fruit> fruits = new ArrayList<Fruit>();
        List<Apple> apples = new ArrayList<Apple>();
        max(fruits);
        max(apples);
    }


    class Fruit implements Comparable<Fruit> {
        @Override
        public int compareTo(Fruit o) {
            return 0;
        }
    }

    class Apple extends Fruit {
    }
}
```

### 捕获转换（capture conversion）

捕获转换是将**通配符参数化类型**转化成**具体参数化类型**的过程。

我们知道以下代码无法通过编译：

```java
public static void testGetSet(ArrayList<?> list){
  list.add(list.get(0)); // list.add的方法都会失败
}
```

但我们加一个helper就可以成功：

```java
public static void testGetSet(ArrayList<?> list){
  getSetHelper(list);
}

public static <T>  void getSetHelper(ArrayList<T> list){
  list.add(list.get(0));
}
```
对于这其中的原理，我们可以简单这样理解，testGetSet接收的ArrayList可以是装任意类型的ArrayList容器，但可以肯定的是必须是一种类型。

而调用getSetHelper就是由于这种确定性，使得list可以传到getSetHelper当中，而对于确定的类型，list.add的操作自然是合法的。

这个解释只处于理解层面，更深层的原理可以参见Going wild with generics(https://www.ibm.com/developerworks/java/library/j-jtp04298/index.html), Part 1，以及官方文档(https://docs.oracle.com/javase/tutorial/java/generics/capture.html)。

## 自限定类型

假设某一个描述生物属性的类Creature，有一个物种属性species；另外一个描述猫的类Cat，有一个属性为毛色coatColor，定义如下：

```java
public class Creature {
    private String species;
    public void setSpecies(String species){
        this.species = species;
    }
    public String getSpecies(){
        return species;
    }
    public static void main(String[] args){
        Cat cat = new Cat();
        cat.setSpecies("cat");
        cat.setCoatColor("red");
        System.out.println(String.format("%s,%s", cat.getSpecies(), cat.getCoatColor()));
    }
}

class Cat extends Creature{
    private String coatColor;
    public void setCoatColor(String coatColor){
        this.coatColor = coatColor;
    }
    public String getCoatColor(){
        return coatColor;
    }
}
```

如果我们要使用时下流行的链式赋值的话，一般我们会在setSpecies和setCoatColor返回this:

```java
public class CreatureL {
    private String species;
    public CreatureL setSpecies(String species){
        this.species = species;
        return this;
    }
    public String getSpecies(){
        return species;
    }
    public static void main(String[] args){
        // 以下代码无法通过编译```
        // CatL cat = new CatL().setSpecies("cat").setCoatColor("red");
        // 并没有达到链式调用的目的
        CatL cat = new CatL();
        cat.setSpecies("cat");
        cat.setCoatColor("red");
        System.out.println(String.format("%s,%s", cat.getSpecies(), cat.getCoatColor()));
    }
}

class CatL extends CreatureL{
    private String coatColor;
    public CatL setCoatColor(String coatColor){
        this.coatColor = coatColor;
        return this;
    }
    public String getCoatColor(){
        return coatColor;
    }
}
```

查看无法通过编译的那一行，原因是setSpeices返回CreateL，这个类并没有setCoatColor方法。所以能够实现链式调用的前提是基类的set方法能够返回子类类型。如何才能实现呢？看一下以下这个代码：

```java
public class CreatureSelfBounded<T extends CreatureSelfBounded<T>> {
    private String species;

    public T setSpecies(String species) {
        this.species = species;
        return (T) this;
    }

    public String getSpecies() {
        return species;
    }

    public static void main(String[] args) {
        CatSelfBounded cat = new CatSelfBounded().setSpecies("cat").setCoatColor("red");
        System.out.println(String.format("%s,%s", cat.getSpecies(), cat.getCoatColor()));
    }
}

class CatSelfBounded extends CreatureSelfBounded<CatSelfBounded> {
    private String coatColor;

    public CatSelfBounded setCoatColor(String coatColor) {
        this.coatColor = coatColor;
        return this;
    }

    public String getCoatColor() {
        return coatColor;
    }
}
```

这一程序实现了链式调用的目的而且运行得还算可以，我们来理解一下：

首先CreatureSelfBounded为自限定类型，**即将定义的泛型类做为自己的边界来使用**，有时候也称之为**泛型循环**。

也就是说这个CreatureSelfBounded类的泛型类型参数必须是自己本身或者是继承自己的子类。关键地，setPecies返回将this强转成了类型T，这个T可能是其子类，也就是说这个基类有了返回自己子类的能力。

接着CatSelfBounded继承了CreatureSelfBounded，此时对于setSpecies来说，T指代CatSelfBounded，返回的类型也自然成了CatSelfBounded。

这里用到自限定类型的一个重要的用法，在基类中使用导出类做为返回值。与此类似，还可以使用导出类做为参数：

```java
interface SelfBoundSetter<T extends SelfBoundSetter<T>> {
    void set(T arg);
}
```

自限定并非完全强制的，在上一个例子中，我们希望子类定义都为Class Sub extends CreatureSelfBounded<Sub>这种类型的，但以下这个子类也能通过编译：

```java
// 以下这个语句无法通过编译
// SmallCatSelfBounded scat = new SmallCatSelfBounded().setSpecies("smallcat").setSize();

class SmallCatSelfBounded extends CreatureSelfBounded<CatSelfBounded>{
    private Integer size;
    public SmallCatSelfBounded setSize(Integer size){
        this.size = size;
        return this;
    }
    public Integer getSize(){
        return size;
    }
}
```

回到setSpecies的代码中，通过字节码会发现这个强制类型转换的(T) this，并没有真正产生指令。真正产生作用的是在main函数中调用setSpecies返回的CreatureSelfBounded类型进行了强制转换成CatSelfBounded。但这个强制转换不能省，不然编译器无法检查通过：

```java
 public CreatureSelfBounded();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return

  public T setSpecies(java.lang.String);
    Code:
       0: aload_0
       1: aload_1
       2: putfield      #2                  // Field species:Ljava/lang/String;
       5: aload_0
       6: areturn

  public java.lang.String getSpecies();
    Code:
       0: aload_0
       1: getfield      #2                  // Field species:Ljava/lang/String;
       4: areturn

  public static void main(java.lang.String[]);
    Code:
       0: new           #3                  // class CatSelfBounded
       3: dup
       4: invokespecial #4                  // Method CatSelfBounded."<init>":()V
       7: ldc           #5                  // String cat
       9: invokevirtual #6                  // Method CatSelfBounded.setSpecies:(Ljava/lang/String;)LCreatureSelfBounded;
      12: checkcast     #3                  // class CatSelfBounded
      15: ldc           #7                  // String red
      17: invokevirtual #8                  // Method CatSelfBounded.setCoatColor:(Ljava/lang/String;)LCatSelfBounded;
      20: astore_1
      21: getstatic     #9                  // Field java/lang/System.out:Ljava/io/PrintStream;
      24: ldc           #10                 // String %s,%s
      26: iconst_2
      27: anewarray     #11                 // class java/lang/Object
      30: dup
      31: iconst_0
      32: aload_1
      33: invokevirtual #12                 // Method CatSelfBounded.getSpecies:()Ljava/lang/String;
      36: aastore
      37: dup
      38: iconst_1
      39: aload_1
      40: invokevirtual #13                 // Method CatSelfBounded.getCoatColor:()Ljava/lang/String;
      43: aastore
      44: invokestatic  #14                 // Method java/lang/String.format:(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
      47: invokevirtual #15                 // Method java/io/PrintStream.println:(Ljava/lang/String;)V
      50: return
```

### 自限定与枚举

Java中的枚举类型，其实是一个语法糖，例如如下的枚举定义：

```java
public enum CatPecies {
    Persian,ScotishFold,Ragdoll
}
```

使用javap返回的反编译代码如下：

```java
public final class CatPecies extends java.lang.Enum<CatPecies> {
  public static final CatPecies Persian;

  public static final CatPecies ScotishFold;

  public static final CatPecies Ragdoll;

  public static CatPecies[] values();
    Code:
       0: getstatic     #1                  // Field $VALUES:[LCatPecies;
       3: invokevirtual #2                  // Method "[LCatPecies;".clone:()Ljava/lang/Object;
       6: checkcast     #3                  // class "[LCatPecies;"
       9: areturn

  public static CatPecies valueOf(java.lang.String);
    Code:
       0: ldc           #4                  // class CatPecies
       2: aload_0
       3: invokestatic  #5                  // Method java/lang/Enum.valueOf:(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;
       6: checkcast     #4                  // class CatPecies
       9: areturn

  static {};
    Code:
       0: new           #4                  // class CatPecies
       3: dup
       4: ldc           #7                  // String Persian
       6: iconst_0
       7: invokespecial #8                  // Method "<init>":(Ljava/lang/String;I)V
      10: putstatic     #9                  // Field Persian:LCatPecies;
      13: new           #4                  // class CatPecies
      16: dup
      17: ldc           #10                 // String ScotishFold
      19: iconst_1
      20: invokespecial #8                  // Method "<init>":(Ljava/lang/String;I)V
      23: putstatic     #11                 // Field ScotishFold:LCatPecies;
      26: new           #4                  // class CatPecies
      29: dup
      30: ldc           #12                 // String Ragdoll
      32: iconst_2
      33: invokespecial #8                  // Method "<init>":(Ljava/lang/String;I)V
      36: putstatic     #13                 // Field Ragdoll:LCatPecies;
      39: iconst_3
      40: anewarray     #4                  // class CatPecies
      43: dup
      44: iconst_0
      45: getstatic     #9                  // Field Persian:LCatPecies;
      48: aastore
      49: dup
      50: iconst_1
      51: getstatic     #11                 // Field ScotishFold:LCatPecies;
      54: aastore
      55: dup
      56: iconst_2
      57: getstatic     #13                 // Field Ragdoll:LCatPecies;
      60: aastore
      61: putstatic     #1                  // Field $VALUES:[LCatPecies;
      64: return
}
```

大概的意思是通过enum定义的枚举类型，实际是继承了java.lang.Enum类，与普通的类对比除了无法继承等没有太大区别。而定义的枚举成员，都为这个类的对象，也是这个类的静态成员。这些都进行了静态初使化，并将所有的对象都放入了VALUES数组中。

这里我们关心这个类的定义：class CatPecies extends java.lang.Enum，而Enum本身是一个自限定泛型类，实现了Compareable和Serializable接口：

```java
public abstract class Enum<E extends Enum<E>> implements Comparable<E>, Serializable{
    // 省略了一些其它方法
    public final int compareTo(E o) {
        // 省略了方法体
    }
}
```

这里的自限定的作用是啥呢？

我们留意一下Enum的Comparable实现compareTo方法，其接受的参数是E，即Enum的类型参数，对于使用enum关键字创建的枚举类来说，这个类型参数的值为正在定义的枚举类，这是编译器强制要求的。

这就产生了一个重要的限制，只有两个类型一致的枚举类型才能进行比较。这也就是这个自限定类型的重要作用。

## 被忽略的语法

考虑一下返回参数是泛型的情况。我们创建了一个非常简单的类，可能这个类并没有实际意义，只是出于演示效果，但在某些复杂的场景下会产生类似的问题。

```java
class Dummy {
    private Object object;
    public <T> T get(){
        return (T) object;
    }
    public <T> void set(T t){
        object = t;
    }
}
```

如果我们要运行如下代码：

```java
Dummy dummy = new Dummy();
dummy.set("hello world");
// 留意以下这个语句
String[] s = dummy.get().split(" ");

System.out.println(Arrays.toString(s));
```

发现编译无法通过，因为dummy.get()没有任何可以用来推断返回值类型的线索，它不像String h = dummy.get()这样用赋值暗示返回的是String。

此时编译器怎么处理呢？它按类型擦除的原则，将dummy.get()返回的值解析成泛型的边界（这里是Object），自然无法在Object上调用split方法，导致编译失败。

有什么办法解决这个问题吗？除了使用中间变量赋值的办法？也是无意中看到以下这个语法可以解决这个问题：

```java
String[] s = dummy.<String>get().split(" ");
```

查了Java官方的Specification（https://docs.oracle.com/javase/specs/jls/se7/html/jls-15.html#jls-15.12），发现对于方法调用，本来就支持在调用的方法前面添加非通配符的类型参数。

```java
MethodInvocation:
   MethodName ( ArgumentListopt )
   Primary . NonWildTypeArgumentsopt Identifier ( ArgumentListopt )
   super . NonWildTypeArgumentsopt Identifier ( ArgumentListopt)
   ClassName . super . NonWildTypeArgumentsopt Identifier ( ArgumentListopt )
   TypeName . NonWildTypeArguments Identifier ( ArgumentListopt)
```

这里的NonWildTypeArgumentsopt指的就是返回值的类型暗示。对于这种语法，也可以在返回值为非泛型的函数上使用，但不会有任何效果。

另外需要说明的是这只是欺骗编译器的手段，并不会生成任何的额外的字节码。
