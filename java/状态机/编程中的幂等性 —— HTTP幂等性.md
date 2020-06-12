看在前面
------

* <a href="https://www.i3geek.com/archives/841">编程中的幂等性 —— HTTP幂等性</a>

百度百科
------

> 幂等（idempotent、idempotence）是一个数学与计算机学概念，常见于抽象代数中。在编程中.一个幂等操作的特点是其任意多次执行所产生的影响均与一次执行的影响相同。幂等函数，或幂等方法，是指可以使用相同参数重复执行，并能获得相同结果的函数。这些函数不会影响系统状态，也不用担心重复执行会对系统造成改变。例如，“getUsername()和setTrue()”函数就是一个幂等函数.更复杂的操作幂等保证是利用唯一交易号(流水号)实现.

什么是幂等性(Idempotence)？
------

HTTP/1.1规范中幂等性的定义

> Methods can also have the property of “idempotence” in that (aside from error or expiration issues) the side-effects of N > 0 identical requests is the same as for a single request.

从定义上看，HTTP方法的幂等性是指一次和多次请求某一个资源应该具有同样的副作用。说白了就是，**同一个请求，发送一次和发送N次效果是一样的**！幂等性是分布式系统设计中十分重要的概念，而HTTP的分布式本质也决定了它在HTTP中具有重要地位。下面将以HTTP中的幂等性做例子加以介绍。


