package com.demotransfer.spring.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;

public class DemoTransferApplicationFailedEventListener implements ApplicationListener<ApplicationFailedEvent> {

	private static final Logger logger = LoggerFactory.getLogger(DemoTransferApplicationFailedEventListener.class);

	@Override
	public void onApplicationEvent(ApplicationFailedEvent event) {
		logger.info("execute demotransfer application failed event......");
	}

}
