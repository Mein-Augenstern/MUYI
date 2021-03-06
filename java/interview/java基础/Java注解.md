What（注解）
====

我们都知道在Java代码中使用注释是为了提升代码的可读性，也就是说，注释是给人看的（对于编译器来说没有意义）。注解可以看做是注释的“强力升级版&#34;，它可以向编译器、虚拟机等解释说明一些事情（也就是说它对编译器等工具也是“可读”的）。比如我们非常熟悉的@Override注解，它的作用是告诉编译器它所注解的方法是重写的父类中的方法，这样编译器就会去检查父类是否存在这个方法，以及这个方法的签名与父类是否相同。

也就是说，注解是描述Java代码的代码，它能够被编译器解析，注解处理工具在运行时也能够解析注解。除了向编译器等传递一些信息，我们也可以使用注解生成代码。比如我们可以使用注解来描述我们的意图，然后让注解解析工具来解析注解，以此来生成一些”模板化“的代码。比如Hibernate、Spring等框架大量使用了注解，来避免一些重复的工作。**注解是一种”被动“的信息，必须由编译器或虚拟机来“主动”解析它，它才能发挥自己的作用**。

注解（Annotation）提供了一种安全的类似注释的机制，为我们在代码中添加信息提供了一种形式化得方法，使我们可以在稍后某个时刻方便的使用这些数据（通过解析注解来使用这些数据），用来将任何的信息或者元数据与程序元素（类、方法、成员变量等）进行关联。其实就是更加直观更加明了的说明，这些说明信息与程序业务逻辑没有关系，并且是供指定的工具或框架使用的。Annotation像一种修饰符一样，应用于包、类型、构造方法、方法、成员变量、参数及本地变量的申明语句中。

Annotation其实是一种接口。通过Java的反射机制相关的API来访问Annotation信息。相关类（框架或工具中的类）根据这些信息来决定如何使用该程序元素或改变它们的行为。Java语言解释器在工作时会忽略这些Annotation，因此在JVM中这些Annotation是“不起作用”的，只能通过配套的工具才能对这些Annotation类型的信息进行访问和处理。

Annotation和interface的异同
------

* annotition的类型使用关键字@interface而不是interface。它继承了java.lang.annotition.Annotition接口，并非申明了一个interface。

* Annotation类型、方法定义是独特的、受限制的。Annotation类型的方法必须申明为无参数、无异常抛出的。这些方法定义了Annotation的成员：方法名称为了成员名，而方法返回值称为了成员的类型。而方法返回值必须为primitive类型、Class类型、枚举类型、Annotation类型或者由前面类型之一作为元素的一位数组。方法的后面可以使用default和一个默认数值来申明成员的默认值，null不能作为成员的默认值，这与我们在非Annotation类型中定义方法有很大不同。Annotation类型和他的方法不能使用Annotation类型的参数，成员不能是generic。只有返回值类型是Class的方法可以在Annotation类型中使用generic，因为此方法能够用类转换将各种类型转换为Class。

* Annotation类型又与接口有着近似之处。它们可以定义常量、静态成员类型（比如枚举类型定义）。Annotation类型也可以如接口一般被实现或者继承。
