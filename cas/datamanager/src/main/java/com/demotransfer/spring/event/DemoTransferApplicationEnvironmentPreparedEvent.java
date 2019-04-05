package com.demotransfer.spring.event;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

public class DemoTransferApplicationEnvironmentPreparedEvent implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

	private static final Logger logger = LoggerFactory.getLogger(DemoTransferApplicationEnvironmentPreparedEvent.class);

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		logger.info("execute demotransfer environment prepared event......");

		ConfigurableEnvironment environment = event.getEnvironment();
		MutablePropertySources propertySources = environment.getPropertySources();
		if (propertySources != null) {
			Iterator<PropertySource<?>> iterator = propertySources.iterator();
			while (iterator.hasNext()) {
				PropertySource<?> next = iterator.next();
				logger.info("propertySource name:{} source:{} class:{}", next.getName(), next.getSource(), next.getClass());
			}
		}
	}

}
