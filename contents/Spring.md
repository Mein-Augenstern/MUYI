Spring源码阅读
------

* <a href="https://github.com/seaswalker/spring-analysis">Spring源码阅读Github笔记</a>

Spring事务管理
------

* Spring事务管理介绍

* 回顾数据库事务相关概念

* 事务API介绍

	* 接口介绍
	
	* PlatformTransactionManager
	
	* TransactionDefinition

	* TransactionStatus
	
	* 编程式事务管理
	
	需要手动编写代码进行事务的管理（一般不用）
	
	* 声明式事务管理
	
		* 基于TransactionProxyFactoryBean的方式
		
		需要为每个事务管理的类配置一个TransactionProxyFactoryBean进行管理。使用时还需要在类中注入该代理类。
		
		* 基于AspectJ的方式（常使用）
		
		配置好之后，按照方法的名字进行管理，无需再类中添加任何东西。
		
		* 基于注解的方式（经常使用）
		
		配置简单，在业务层类上添加注解@Transactional。
	
Spring知识点扫盲
------

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%40Autowired%E5%92%8C%40Resource%E5%8C%BA%E5%88%AB.md">@Autowired和@Resource区别</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%40Repository%E5%92%8C%40Componet%E5%92%8C%40Service%E5%92%8C%40Controller%E4%B9%8B%E9%97%B4%E5%8C%BA%E5%88%AB%E5%92%8C%E8%81%94%E7%B3%BB.md">@Repository和@Componet和@Service和@Controller之间区别和联系</a>

* <a href="https://github.com/DemoTransfer/Java-Guide/blob/master/java/interview/Spring/Spring%20%E4%BA%8B%E5%8A%A1%E9%9D%A2%E8%AF%95%E8%80%83%E7%82%B9.md">Spring事务面试考点</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/Springboot%E5%92%8CSpring%E5%8C%BA%E5%88%AB.md">Springboot和Spring区别</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/Spring%E4%B8%AD%3Cbean%3E%E5%85%83%E7%B4%A0%E5%8F%AF%E4%BB%A5%E9%85%8D%E7%BD%AE%E7%9A%84%E5%B1%9E%E6%80%A7.md">Spring中<bean>元素可以配置的属性</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/Spring%E4%B8%ADBeanFactory%E5%92%8CApplicationContext%E6%9C%89%E4%BB%80%E4%B9%88%E5%8C%BA%E5%88%AB.md">Spring中BeanFactory和ApplicationContext有什么区别</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/Spring%E4%B8%ADIoC%E5%92%8CAOP%E6%A6%82%E5%BF%B5%E4%BB%8B%E7%BB%8D.md">Spring中IOC和AOP概念介绍</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/Spring%E4%B8%AD%E5%B8%B8%E7%94%A8%E6%B3%A8%E8%A7%A3.md">Spring中常用注解</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/Spring%E5%A6%82%E4%BD%95%E7%AE%A1%E7%90%86Bean%E7%A4%BA%E4%BE%8B.md">Spring如何管理Bean示例</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/Spring%E6%94%AF%E6%8C%81%E5%93%AA%E4%BA%9BBean%E4%BD%9C%E7%94%A8%E5%9F%9F.md">Spring支持哪些Bean作用域</a>

* <a href="https://github.com/DemoTransfer/Java-Guide/blob/master/java/interview/Spring/Spring%E5%BC%82%E6%AD%A5%E8%B0%83%E7%94%A8%40Async.md">Spring异步调用@Async</a>

* <a href="https://github.com/DemoTransfer/Java-Guide/blob/master/java/interview/Spring/Spring%E6%94%AF%E6%8C%81%E5%93%AA%E4%BA%9BBean%E4%BD%9C%E7%94%A8%E5%9F%9F.md">Spring支持哪些Bean作用域</a>

* <a href="https://github.com/DemoTransfer/Java-Guide/blob/master/java/interview/Spring/%E5%BC%82%E6%AD%A5%E8%AF%B7%E6%B1%82%E4%B8%8E%E5%BC%82%E6%AD%A5%E8%B0%83%E7%94%A8%E7%9A%84%E5%8C%BA%E5%88%AB.md">异步请求和异步调用的区别</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E7%AE%80%E5%8D%95%E4%BB%8B%E7%BB%8D%E4%B8%8Bservlet%E7%9A%84%E7%94%9F%E5%91%BD%E5%91%A8%E6%9C%9F%EF%BC%8Cservlet%E6%98%AF%E5%90%A6%E4%BC%9A%E5%A4%9A%E6%AC%A1%E5%88%9D%E5%A7%8B%E5%8C%96%EF%BC%9F.md">简单介绍下servlet的生命周期，servlet是否会多次初始化</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E8%B0%88%E8%B0%88Spring%E4%B8%ADBean%E7%9A%84%E7%94%9F%E5%91%BD%E5%91%A8%E6%9C%9F.md">探探Spring中Bean的生命周期</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E8%B0%88%E8%B0%88Spring%E4%B8%AD%E5%B8%B8%E7%94%A8%E8%AE%BE%E8%AE%A1%E6%A8%A1%E5%BC%8F.md">谈谈Spring中常用设计模式</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E8%B0%88%E8%B0%88Spring%E5%BE%AA%E7%8E%AF%E4%BE%9D%E8%B5%96.md">谈谈Spring循环依赖</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E8%B0%88%E8%B0%88Spring%E6%A1%86%E6%9E%B6%E7%9A%84BeanFactory.md">谈谈Spring框架的BeanFactory</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E8%B0%88%E8%B0%88Spring%E6%A1%86%E6%9E%B6%E7%9A%84FactoryBean.md">谈谈Spring框架的FactoryBean</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E8%B0%88%E8%B0%88Spring%E6%A1%86%E6%9E%B6%E7%9A%84%E4%BE%9D%E8%B5%96%E6%B3%A8%E5%85%A5%EF%BC%88DI%EF%BC%89.md">谈谈Spring框架的依赖注入（DI）</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/Spring/%E8%BF%87%E6%BB%A4%E5%99%A8%E3%80%81%E7%9B%91%E5%90%AC%E5%99%A8%E3%80%81%E6%8B%A6%E6%88%AA%E5%99%A8%E7%9A%84%E5%8C%BA%E5%88%AB.md">过滤器、监听器、拦截器的区别</a>
