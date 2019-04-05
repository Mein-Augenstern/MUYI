package com.demotransfer.spring.beanwrapper;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;

public class BeanWrapperTest {

	public static void main(String[] args) {
		User user = new User();
		BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(user);
		beanWrapper.setPropertyValue("username", "张三");
		System.out.println(user.getUsername());

		PropertyValue propertyValue = new PropertyValue("username", "李四");
		beanWrapper.setPropertyValue(propertyValue);
		System.out.println(user.getUsername());
	}
}
