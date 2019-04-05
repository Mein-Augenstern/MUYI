package com.demotransfer.config.properties;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description: properties文件加载器
 * @author: Administrator
 * @date: 2018年11月12日 下午10:33:33
 */
public class PropertiesLoader {

	private static final Logger logger = LoggerFactory.getLogger(PropertiesLoader.class);

	// properties file cache buffer
	private static Map<String, String> propertiesContentCache = new ConcurrentHashMap<String, String>();

	static {
		// default load application.properties
		InputStream applicationStream = null;
		try {
			applicationStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties");

			Properties properties = new Properties();
			properties.load(applicationStream);

			for (Map.Entry<Object, Object> entry : properties.entrySet()) {
				propertiesContentCache.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
			}

			if (logger.isTraceEnabled()) {
				logger.trace("loading application size:{}", properties.size());
			}
		} catch (Exception e) {
			logger.error("loading application.properties failed......", e);
		} finally {
			IOUtils.closeQuietly(applicationStream);
		}
	}

	/**
	 * loading key corresponding value
	 * 
	 * @param: @param
	 *             key:properties file content key
	 * @return: when key empty then return empty or return cache buffer value.
	 */
	public static String getValue(String key) {
		if (StringUtils.isBlank(key)) {
			logger.debug("loading key empty...");
			return StringUtils.EMPTY;
		}

		return propertiesContentCache.get(key);
	}

}
