package com.demotransfer.config.database;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.alibaba.druid.pool.DruidDataSource;
import com.demotransfer.config.properties.PropertiesCacheUtils;

@Configuration
public class DataSourceConfig implements EnvironmentAware {

	private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

	@Override
	public void setEnvironment(Environment environment) {
		PropertiesCacheUtils.setPropertiesToCacheContext("application.properties");
	}

	@Bean
	public DataSource getDataSource() {
		try {
			DruidDataSource druidDataSource = new DruidDataSource();
			druidDataSource.setUrl(PropertiesCacheUtils.getValueByKey("spring.datasource.url"));
			druidDataSource.setUsername(PropertiesCacheUtils.getValueByKey("spring.datasource.username"));
			druidDataSource.setPassword(PropertiesCacheUtils.getValueByKey("spring.datasource.password"));
			druidDataSource.setDriverClassName(PropertiesCacheUtils.getValueByKey("spring.datasource.driverClassName"));
			druidDataSource.setDbType(PropertiesCacheUtils.getValueByKey("spring.datasource.type"));
			return druidDataSource;
		} catch (Exception e) {
			logger.error("get database connection failed:{]", e);
			throw new BeanCreationException("create bean:{} failed:{}", DruidDataSource.class.getName(), e);
		}
	}

	@Bean
	public SqlSessionFactory getSqlSessionFactory() {
		try {
			SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
			sessionFactoryBean.setDataSource(getDataSource());
			return sessionFactoryBean.getObject();
		} catch (Exception e) {
			logger.error("get sqlSessionFactoru failed:{}", e);
			throw new BeanCreationException("create bean:{} failed:{}", SqlSessionFactory.class.getName(), e);
		}
	}

	@Bean
	public SqlSessionTemplate getSqlSessionTemplate() {
		try {
			return new SqlSessionTemplate(getSqlSessionFactory());
		} catch (Exception e) {
			logger.error("get sqlSessionTemplate failed:{}", e);
			throw new BeanCreationException("create bean:{} failed:{}", SqlSessionTemplate.class.getName(), e);
		}
	}

}
