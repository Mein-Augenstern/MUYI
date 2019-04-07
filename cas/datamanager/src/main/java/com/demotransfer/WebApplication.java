package com.demotransfer;

import org.apache.commons.lang3.StringUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import com.alibaba.druid.pool.DruidDataSource;
import com.demotransfer.config.properties.PropertiesCacheUtils;

@SpringBootApplication
@MapperScan("com.demotransfer.module.login.dao")
public class WebApplication extends SpringBootServletInitializer implements EmbeddedServletContainerCustomizer {

	private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(WebApplication.class);
	}

	public static void main(String[] args) {
		ConfigurableApplicationContext applicationContext = SpringApplication.run(WebApplication.class, args);

		DruidDataSource dataSource = (DruidDataSource) applicationContext.getBean("datasource");
		logger.info("datasource password:{}", dataSource.getPassword());
	}

	@Override
	public void customize(ConfigurableEmbeddedServletContainer configurableEmbeddedServletContainer) {
		// default springboot port
		int springbootStartPort = 8080;
		try {
			String propertiesPort = PropertiesCacheUtils.getValue("springboot.start.port");
			if (StringUtils.isNotBlank(propertiesPort)) {
				springbootStartPort = Integer.parseInt(propertiesPort);
			}
		} catch (Exception e) {
		}
		configurableEmbeddedServletContainer.setPort(springbootStartPort);

		// default springboot contextPath
		String springbootContentPath = "/demotransfer";
		try {
			String propertiesContentPath = PropertiesCacheUtils.getValue("springboot.start.contextPath");
			if (StringUtils.isNotBlank(propertiesContentPath)) {
				springbootContentPath = propertiesContentPath;
			}
		} catch (Exception e) {
		}
		configurableEmbeddedServletContainer.setContextPath(springbootContentPath);
	}

}
