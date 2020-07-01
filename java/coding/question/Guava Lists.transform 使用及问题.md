看在前面
------

* <a href="https://www.jianshu.com/p/3e3bf25d7878">Guava Lists.transform 使用及问题</a>

一、Lists.transform的使用
------

大家在写代码的过程中肯定会碰到一种状况，dao中查询数据库返回了一个结果集list<Result>，其中Result对象中包含了Id，nameStr，msg等字段，但是上层业务的一些接口参数可能只需要id的结果集list<Integer>，当然我们可以使用for each循环list<Result>然后将Id取出依次add到list<Integer>中，Lists.transform就是帮我们方便的解决这一过程，能够轻松的从一种类型的list转换为另一种类型的list。使用方式如下：
  
```java
public void listToList(){
  //源list
  List<Result> listResults = Lists.newArrayList(new Result(1,"test1"),new Result(2,"test2"),new Result(3,"test3"));
  //转换为目标list
  List<String> strLists = Lists.transform(listResults,new Function<Result,String>(){
    @Override
    public String apply(Result result){
      return result.getNameStr();
    }
  });
}
```

二、Lists.transform使用可能遇到的问题
------

请看如下代码，只是在上面的示例中增加了一行改变listResults中对象属性的操作和打印：

```java
 @Test
public void listToList(){
  //源list
  List<Result> listResults = Lists.newArrayList(new Result(1,"test1"),new Result(2,"test2"),new Result(3,"test3"));
  //转换为目标list
  List<String> strLists = Lists.transform(listResults,new Function<Result,String>(){
    @Override
    public String apply(Result result){
      return result.getNameStr();
    }
  });
  //转换后目标list打印
  System.out.println("strLists 1 values:");
  for(String str:strLists){
    System.out.println(str+";");
  }
  //修改源list的值
  for(Result result:listResults){
    result.setNameStr("reset");
  }
  //再次打印目标list
  System.out.println("strLists 2 values:");
  for(String str:strLists){
    System.out.println(str+";");
  }
}
```

输出结果是什么呢？如果刚开始使用Lists.transform的话，肯定会认为两次输出是相同的才对，但实际情况却不是这样的，已经转换后得到的list会受到源list的改动而改变，上面代码的输出结果如下：

```java
strLists 1 values:
test1;
test2;
test3;
strLists 2 values:
reset;
reset;
reset;
```

这个坑如果不了解就使用Lists.transform的话那么问题有点大了，会造成很严重的问题，我们先来看下Lists.transform是怎样实现的。

```java
public static <F, T> List<T> transform(List<F> fromList, Function<? super F, ? extends T> function) {
    return (List)(fromList instanceof RandomAccess ? new Lists.TransformingRandomAccessList(fromList, function) : new Lists.TransformingSequentialList(fromList, function));
}
```

可以看到上面的代码写的很清楚，Lists.transform返回的是一个新创建的TransformingSequentialList实例，然后我们再接着往下看

```java
public ListIterator<T> listIterator(int index) {
    return new TransformedListIterator<F, T>(this.fromList.listIterator(index)) {
        T transform(F from) {
            return TransformingSequentialList.this.function.apply(from);
        }
    };
}
```

TransformingSequentialList每次遍历都会从原来的list中遍历来从新计算得到function

**使用新的开源类库时最后多做了解，看下源码后再使用，这样就能在合适的场景下合理使用，如果不了解的话还是最好先用比较稳妥的方式进行编码。**
