package com.demotransfer.spring.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

public class DemoTransferApplicationStartedEventListener implements ApplicationListener<ApplicationStartingEvent> {

	private static final Logger logger = LoggerFactory.getLogger(DemoTransferApplicationStartedEventListener.class);

	@Override
	public void onApplicationEvent(ApplicationStartingEvent event) {
		logger.info("execute starting demotransfer application event......");
	}

}
