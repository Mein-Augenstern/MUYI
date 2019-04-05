package com.demotransfer;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.web.support.SpringBootServletInitializer;

import com.demotransfer.config.properties.PropertiesLoader;

@SpringBootApplication
public class WebApplication extends SpringBootServletInitializer implements EmbeddedServletContainerCustomizer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(WebApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(WebApplication.class, args);
	}

	@Override
	public void customize(ConfigurableEmbeddedServletContainer configurableEmbeddedServletContainer) {
		// default springboot port
		int springbootStartPort = 8080;
		try {
			String propertiesPort = PropertiesLoader.getValue("springboot.start.port");
			if (StringUtils.isNotBlank(propertiesPort)) {
				springbootStartPort = Integer.parseInt(propertiesPort);
			}
		} catch (Exception e) {
		}
		configurableEmbeddedServletContainer.setPort(springbootStartPort);

		// default springboot contextPath
		String springbootContentPath = "/demotransfer";
		try {
			String propertiesContentPath = PropertiesLoader.getValue("springboot.start.contextPath");
			if (StringUtils.isNotBlank(propertiesContentPath)) {
				springbootContentPath = propertiesContentPath;
			}
		} catch (Exception e) {
		}
		configurableEmbeddedServletContainer.setContextPath(springbootContentPath);
	}

}
