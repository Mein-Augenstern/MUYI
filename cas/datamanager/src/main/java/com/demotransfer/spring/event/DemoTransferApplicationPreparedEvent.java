package com.demotransfer.spring.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;

public class DemoTransferApplicationPreparedEvent implements ApplicationListener<ApplicationPreparedEvent> {

	private static final Logger logger = LoggerFactory.getLogger(DemoTransferApplicationPreparedEvent.class);

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent event) {
		logger.info("execute demo transfer application prepared event......");
	}

}
