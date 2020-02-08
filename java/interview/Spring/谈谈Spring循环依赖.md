看在前面
====

> * <a href="https://blog.csdn.net/chejinqiang/article/details/80003868">Spring循环依赖及解决方式</a>

> * <a href="https://www.jianshu.com/p/988ad2913b76">Spring循环依赖解决</a>

Question
====

* 什么是循环依赖？

* 可以提前检测到循环依赖的存在么？

* Spring怎么解决循环依赖？

理解Spring中出现的循环依赖，可以从spring如何创建一个对象，以及如何处理bean间引用，以及spring使用何种策略处理循环引用问题说起。

------

一、 什么是循环依赖？
====

循环依赖其实就是循环引用，也就是两个或者两个以上的bean互相持有对方，最终形成闭环。比如A依赖于B，B依赖于C，C又依赖于A。注意，这里不是函数的循环调用，是对象的相互依赖关系。循环调用其实就是一个死循环，除非有终结条件。

Spring中循环依赖场景有：

* 构造器的循环依赖 

* field属性的循环依赖

其中，**构造器的循环依赖问题无法解决，只能拋出BeanCurrentlyInCreationException异常**，**在解决属性循环依赖时，spring采用的是提前暴露对象的方法**。

二、怎么检测是否存在循环依赖
====

检测循环依赖相对比较容易，Bean在创建的时候可以给该Bean打标，如果递归调用回来发现正在创建中的话，即说明了循环依赖了。

emmm？

三、Spring怎么解决循环依赖
====

**<h3>3.1 关于循环依赖</h3>**

Spring的循环依赖的理论依据基于Java的引用传递，当获得对象的引用时，对象的属性是可以延后设置的。（但是构造器必须是在获取引用之前）

Spring的单例对象的初始化主要分为三步： 

1. ```createBeanInstance```：实例化，其实也就是调用对象的构造方法实例化对象

2. ```populateBean```：填充属性，这一步主要是多bean的依赖属性进行填充

3. ```initializeBean```：调用```spring xml```中的```init```方法。

从上面单例bean的初始化可以知道：循环依赖主要发生在第一、二步，也就是构造器循环依赖和field循环依赖。那么我们要解决循环引用也应该从初始化过程着手，对于单例来说，在Spring容器整个生命周期内，有且只有一个对象，所以很容易想到这个对象应该存在Cache中，Spring为了解决单例的循环依赖问题，使用了三级缓存。

这个三级缓存分别指： 

* ```singletonFactories``` ： 进入实例化阶段的单例对象工厂的 cache （三级缓存）

* ```earlySingletonObjects``` ：完成实例化但是尚未初始化的，提前暴光的单例对象的 cache （二级缓存）

* ```singletonObjects```：完成初始化的单例对象的 cache（一级缓存）

其中上述三个属性可以参照Spring中```DefaultSingletonBeanRegistry```类源码。

```java
/** Cache of singleton objects: bean name --> bean instance */
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<String, Object>(256);

/** Cache of singleton factories: bean name --> ObjectFactory */
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>(16);

/** Cache of early singleton objects: bean name --> bean instance */
private final Map<String, Object> earlySingletonObjects = new HashMap<String, Object>(16);
```

官方对此的属性定义不是很明确，这里我们可以这样来理解。

* ```singletonFactories```：用于存储在spring内部所使用的beanName->对象工厂的引用，一旦最终对象被创建 (通过objectFactory.getObject())，此引用信息将删除

* ```earlySingletonObjects```：用于存储在创建Bean早期对创建的原始bean的一个引用，注意这里是原始bean，即使用工厂方法或构造方法创建出来的对象，一旦对象最终创建好，此引用信息将删除

从上面的解释，可以看出，这两个对象都是一个临时工。在所有的对象创建完毕之后，此两个对象的size都为0。那么再来看下Spring源码中这两个对象如何进行协作：

<h4>方法一</h4>

```java
	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}
```

在方法1中，对象信息对```beanFactory```的形式被放入```singletonFactories```中，这时```earlySingletonObjects```中肯定没有此对象(因为remove)。

<h4>方法二</h4>

```java
	/**
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			synchronized (this.singletonObjects) {
				singletonObject = this.earlySingletonObjects.get(beanName);
				if (singletonObject == null && allowEarlyReference) {
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return (singletonObject != NULL_OBJECT ? singletonObject : null);
	}
```

在方法2中，在一定条件下（```allowEarlyReference```为true）的条件下，对象从```singleFactories```中的```objectFactory```中被取出来，同时remove掉，被放入```earlySingletonObjects```中。这时,```earlySingletonObjects```就持有对象信息了；当然，如果```allowEarlyReference```为false的情况下，且```earlySingletonObjects```本身就没有持有对象的情况下，肯定不会将对象从```objectFactory```中取出来的。这个很重要，因为后面将根据此信息进行循环引用处理。

其中解决循环依赖问题的关键点就在 ```singletonFactory.getObject()``` 这一步，```getObject``` 这是 ```ObjectFactory<T>``` 接口的方法。Spring 通过对该方法的实现，在 ```createBeanInstance``` 之后，```populateBean``` 之前，通过将创建好但还没完成属性设置和初始化的对象提前曝光，然后再获取 Bean 的时候去看是否有提前曝光的对象实例来判断是否要走创建流程。

<h4>方法三</h4>

```java
	/**
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, (singletonObject != null ? singletonObject : NULL_OBJECT));
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}
```

在方法3中，对象被加入到singletonObjects中，同时singletonFactories和earlySingletonObjects中都remove掉持有的对象（不管持有与否），这就表示在之前的处理中，这只相当于一个临时容器，处理完毕之后都会remove掉。

那么，我们来看这3个方法是不是按照先后顺序被调用的呢。代码顺序如下所示：

类AbstracBeanFactory获取bean。M-1

```java
	protected <T> T doGetBean(final String name, final Class<T> requiredType, final Object[] args, boolean typeCheckOnly) throws BeansException {
		if (mbd.isSingleton()) {
		   sharedInstance = getSingleton(beanName, new ObjectFactory<Object>() {
						public Object getObject() throws BeansException {
							try {
								return createBean(beanName, mbd, args);
							}               ……
	}
```

进入getSingleton方法M-2

```java
	/**
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "'beanName' must not be null");
		synchronized (this.singletonObjects) {
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<Exception>();
				}
				try {
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return (singletonObject != NULL_OBJECT ? singletonObject : null);
		}
	}
```

查看singletonFactory.getObject(),即createBean(beanName, mbd, args),最终转向doCreateBean方法M-3

```java
	protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final Object[] args) {
			// Instantiate the bean.
			BeanWrapper instanceWrapper = null;
						instanceWrapper = createBeanInstance(beanName, mbd, args);
				addSingletonFactory(beanName, new ObjectFactory() {
					public Object getObject() throws BeansException {
						return getEarlyBeanReference(beanName, mbd, bean);
					}
				});
			}
```

上面代码会调用方法addSingletonFactory，即上文所说的方法1。那么方法2会在什么地方调用呢。答案在两个地点。

第一个地方，称之为调用点A，即在最开始获取bean时，会调用。

```java
Object sharedInstance = getSingleton(beanName);
```

此方法最终会调用到

```java
getSingleton(beanName, true)
```

这里传递了参数true。即会尝试解析singletonFactories。然而，在最开始创建对象时，singletonFactories中肯定不会持有对象信息，所以会返回null。

第二个地方，称之为调用点B，即在完成bean创建时，会有一个验证过程。即在方法M-3中，即在调用方法2之前。代码如下：

```java
	if (earlySingletonExposure) {
				Object earlySingletonReference = getSingleton(beanName, false);
				if (earlySingletonReference != null) {
					if (exposedObject == bean) {
						exposedObject = earlySingletonReference;
					}
					else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
						String[] dependentBeans = getDependentBeans(beanName);
						Set<String> actualDependentBeans = new LinkedHashSet<String>(dependentBeans.length);
						for (String dependentBean : dependentBeans) {
							if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
								actualDependentBeans.add(dependentBean);
							}
						}
						if (!actualDependentBeans.isEmpty()) {
							throw 文章开头的异常。
						}
					}
				}
```

调用点B的逻辑有点多，后面的逻辑主要是作循环引用验证。注意在调用点B传递参数为false，即不会解析singletonFactories。

接下来，针对创建bean的不同顺序对调用点和调用方法进行分析。在正常的情况下，调用顺序如下：以下有无，表示是否持有对指定Bean的引用

![Spring循环引用-三级缓存一]()

但是出现循环引用之后呢，就会出现这种情况：

![Spring循环引用-三级缓存二]()

在上面这个过程中，在对A进行验证时，就会从earlySingletonObjects中取得一个A，但是这个A和后面的A-可能不是同一个对象，这是因为有了beanPostProcessor存在，它可以改变bean的最终值，比如对原始bean进行封装，代理等。在这个过程中，出现了3个对象A,A-,B，而B中所持有的A对象为原始的A。如果这里的A和A-不是同一个对象，即产生了beanA有了beanB的引用，但beanB并没有beanA的引用，而是另一个beanA的引用。这肯定不满足条件。

那么我们来看spring对这种情况的处理，即在上文中的方法3，再次将代码贴在下面：

```java
	Object earlySingletonReference = getSingleton(beanName, false);
				if (earlySingletonReference != null) {//判断点1
					if (exposedObject == bean) {//判断点2
						exposedObject = earlySingletonReference;
					}
					else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {判断点3
						String[] dependentBeans = getDependentBeans(beanName);
						Set<String> actualDependentBeans = new LinkedHashSet<String>(dependentBeans.length);
						for (String dependentBean : dependentBeans) {
							if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
								actualDependentBeans.add(dependentBean);
							}
						}
						if (!actualDependentBeans.isEmpty()) {判断点4抛出对象不致异常。
						}
```

上面有4个判断点，依次如下

判断点1，首先确定这个对象能从earlySingletonObjects中取出对象来，经过上面的分析，我们知道，在正常情况下，此对象为null，即不存在循环检测。而在循环引用中，此对象能够被取出来。

判断点2，再判断这个对象和当前通过beanPostProcessor处理过的对象是否相同，如果相同，表示对象没有经过修改，即A=A-，那么循环引用成立。无需处理

判断点3，判断当前对象A是否被其他对象所依赖，在循环引用中，已经处理了A和B，那么在依赖表中，即在属性dependentBeanMap和dependenciesForBeanMap中。其中A->B表示A依赖于B，B->A表示B依赖于A。那么在dependentBeanMap中就会出现两个entry，分别为A->B和B->A。这里A依赖于A，那么表示A已经被依赖，则进入进一步检测中。在检测中，将取得一个A的被依赖列表中的bean已经被创建的对象列表值。

判断点4，如果被依赖对象列表不为空，则表示出现循环引用。因为按照创建规则，如果A->B，则必须先创建B，而B->A，则必须先创建A。在这里，A被B依赖，就要求A必须在B之前被创建，而B又被A依赖，又要求A必须在B之前被创建。这创建的两个对象必须满足一致才可以。即在A->B中的两个对象，必须和B->A的两个对象，互相一致才可以，否则就不是循环引用。

至此，整个流程梳理清楚。那么，如何处理这种循环引用呢？答案其实也很简单，在xml中将两方的循环切掉。然后使用一个beanPostProcessor即可以，此beanPostProcessor必须要在放到所有beanPostPrcessor的最后面。然后此beanPostProcessor，这样写即可：

<h4>基于上述分析，如果下面的情况时</h4>

在使用spring的场景中，有时会碰到如下的一种情况，即bean之间的循环引用。即两个bean之间互相进行引用的情况。这时，在spring xml配置文件中，就会出现如下的配置：

```java
<bean id="beanA" class="BeanA" p:beanB-ref="beaB"/>
<bean id="beanB" class="BeanB" p:beanA-ref="beaA"/>
```

```java
判断当前bean为beanA
BeanB beanB=beanFactory.getBean(“beanB”);
beanA.setBeanB(beanB);
beanB.setBeanA(beanA);
```

**<h3>3.2 获取bean过程和三级缓存之间关系</h3>**

* **step one**：在创建bean的时候，首先想到的是从cache中获取这个单例的bean，这个缓存就是singletonObjects。

*  **step two**：如果获取不到，并且对象正在创建中，就再从二级缓存earlySingletonObjects中获取。

* **step three**：如果还是获取不到且允许singletonFactories通过getObject()获取，就从三级缓存singletonFactory.getObject()(三级缓存)获取，如果获取到了则：从singletonFactories中移除，并放入earlySingletonObjects中。其实也就是从三级缓存移动到了二级缓存。

从上面三级缓存的分析，我们可以知道，**Spring解决循环依赖的诀窍就在于singletonFactories这个三级cache**。这个cache的类型是ObjectFactory。这里就是解决循环依赖的关键，发生在createBeanInstance之后，也就是说单例对象此时已经被创建出来(调用了构造器)。这个对象已经被生产出来了，虽然还不完美（还没有进行初始化的第二步和第三步），但是已经能被人认出来了（根据对象引用能定位到堆中的对象），所以**Spring此时将这个对象提前曝光出来**让大家认识，让大家使用。

**<h3>3.3 Spring提前曝光对象有什么好处？</h3>**

让我们来分析一下“A的某个field或者setter依赖了B的实例对象，同时B的某个field或者setter依赖了A的实例对象”这种循环依赖的情况。

> A首先完成了初始化的第一步，并且将自己提前曝光到singletonFactories中，此时进行初始化的第二步，发现自己依赖对象B，此时就尝试去get(B)，发现B还没有被create，所以走create流程，B在初始化第一步的时候发现自己依赖了对象A，于是尝试get(A)，尝试一级缓存singletonObjects(肯定没有，因为A还没初始化完全)，尝试二级缓存earlySingletonObjects（也没有），尝试三级缓存singletonFactories，由于A通过ObjectFactory将自己提前曝光了，所以B能够通过ObjectFactory.getObject拿到A对象(虽然A还没有初始化完全，但是总比没有好呀)，B拿到A对象后顺利完成了初始化阶段1、2、3，完全初始化之后将自己放入到一级缓存singletonObjects中。此时返回A中，A此时能拿到B的对象顺利完成自己的初始化阶段2、3，最终A也完成了初始化，进去了一级缓存singletonObjects中，而且更加幸运的是，由于B拿到了A的对象引用，所以B现在hold住的A对象完成了初始化。

知道了这个原理时候，肯定就知道为啥Spring不能解决“A的构造方法中依赖了B的实例对象，同时B的构造方法中依赖了A的实例对象”这类问题了！因为加入singletonFactories三级缓存的前提是执行了构造器，所以构造器的循环依赖没法解决。

四、基于构造器的循环依赖
====

Spring容器会将每一个正在创建的Bean 标识符放在一个“当前创建Bean池”中，Bean标识符在创建过程中将一直保持在这个池中，因此如果在创建Bean过程中发现自己已经在“当前创建Bean池”里时将抛出BeanCurrentlyInCreationException异常表示循环依赖；而对于创建完毕的Bean将从“当前创建Bean池”中清除掉。

Spring容器先创建单例A，A依赖B，然后将A放在“当前创建Bean池”中，此时创建B,B依赖C ,然后将B放在“当前创建Bean池”中,此时创建C，C又依赖A， 但是，此时A已经在池中，所以会报错，**因为在池中的Bean都是未初始化完的，所以会依赖错误 ，（初始化完的Bean会从池中移除）**

五、基于setter属性的循环依赖
====

![Spring循环依赖-Spring生命周期]()

我们结合上面那张图看，Spring先是用构造实例化Bean对象 ，创建成功后，Spring会通过以下代码提前将对象暴露出来，此时的对象A还没有完成属性注入，属于早期对象，此时Spring会将这个实例化结束的对象放到一个Map中，并且Spring提供了获取这个未设置属性的实例化对象引用的方法。 结合我们的实例来看，当Spring实例化了A、B、C后，紧接着会去设置对象的属性，此时A依赖B，就会去Map中取出存在里面的单例B对象，以此类推，不会出来循环的问题喽

六、小结
====

不要使用基于构造函数的依赖注入，可以通过以下方式解决：

1. 在字段上使用@Autowired注解，让Spring决定在合适的时机注入

2. 用基于setter方法的依赖注入。
