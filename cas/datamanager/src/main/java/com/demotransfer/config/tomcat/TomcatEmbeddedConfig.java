package com.demotransfer.config.tomcat;

import java.nio.charset.Charset;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * @Description: config springboot emdedded tomcat
 * @author: Administrator
 * @date: 2019年1月6日 下午7:41:33
 */
@Configuration
public class TomcatEmbeddedConfig {

	private final static Logger logger = LoggerFactory.getLogger(TomcatEmbeddedConfig.class);

	// @Bean
	public EmbeddedServletContainerFactory servletContainer() {
		TomcatEmbeddedServletContainerFactory tomcatEmbeddedServletContainerFactory = new TomcatEmbeddedServletContainerFactory();
		tomcatEmbeddedServletContainerFactory.setUriEncoding(Charset.forName("UTF-8"));
		tomcatEmbeddedServletContainerFactory.addAdditionalTomcatConnectors(createSslConnector());
		return tomcatEmbeddedServletContainerFactory;
	}

	private Connector createSslConnector() {
		Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
		Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
		try {
			connector.setScheme("https");

		} catch (Exception e) {

		}
		return connector;
	}

}
