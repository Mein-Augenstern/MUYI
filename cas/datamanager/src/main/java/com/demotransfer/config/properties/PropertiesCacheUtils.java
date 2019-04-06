package com.demotransfer.config.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description: properties profile caches the loaded classes
 * @author guoyangyang 2019年4月6日 下午4:16:02
 * @version V1.0.0
 */
public abstract class PropertiesCacheUtils {

	private static final Logger logger = LoggerFactory.getLogger(PropertiesCacheUtils.class);

	// properties file cache buffer
	private static Map<String, String> propertiesContentCache = new ConcurrentHashMap<String, String>();

	// the fixed encoding of the loading file
	protected static final String fileFixedCoding = "UTF-8";

	static {
		// default load application.properties
		InputStream applicationStream = null;
		try {
			applicationStream = Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("application.properties");

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
	 * get properties file cache buffer
	 * 
	 * @return
	 */
	public static Map<String, String> getPropertiesContentCache() {
		return propertiesContentCache;
	}

	public static void setPropertiesToCacheContext(String propertiesClassLoaderPath) {
		setPropertiesToCacheContext(propertiesClassLoaderPath, StringUtils.EMPTY);
	}

	public static String getValueByKey(String propertiesKey) {
		String result = StringUtils.EMPTY;
		if (propertiesContentCache.size() >= 1) {
			result = propertiesContentCache.get(propertiesKey);
		}
		return result;
	}

	/**
	 * loading properties file(absolute file path) to properties cache
	 * 
	 * @param absoulteFilePath
	 * @param loadCharSet
	 */
	public static void setAbsoluteFileToCacheContext(String absoulteFilePath, String loadCharSet) {
		File file = new File(absoulteFilePath);
		if (!file.exists()) {
			logger.error("loading file:{} not exist.", absoulteFilePath);
			return;
		}

		InputStreamReader inputStreamReader = null;
		try {
			inputStreamReader = new InputStreamReader(new FileInputStream(new File(absoulteFilePath)), loadCharSet);

			setPropertiesToCache(inputStreamReader);
		} catch (Exception e) {
			logger.error("loading properties content to cache failed:{}", e);
		} finally {
			IOUtils.closeQuietly(inputStreamReader);
		}
	}

	/**
	 * loading properties file content to properties cache buffer
	 * 
	 * @param propertiesClassLoaderPath
	 * @param loadCharSet
	 */
	public static void setPropertiesToCacheContext(String propertiesClassLoaderPath, String loadCharSet) {
		InputStreamReader inputStreamReader = null;
		try {
			if (StringUtils.isBlank(loadCharSet)) {
				loadCharSet = fileFixedCoding;
			}

			inputStreamReader = new InputStreamReader(
					Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesClassLoaderPath));
			setPropertiesToCache(inputStreamReader);
		} catch (Exception e) {
			logger.error("loading properties:{} content failed:{}", propertiesClassLoaderPath, e);

			try {
				if (inputStreamReader != null) {
					inputStreamReader.close();
				}
				inputStreamReader = new InputStreamReader(new FileInputStream(new File(propertiesClassLoaderPath)),
						loadCharSet);
				setPropertiesToCache(inputStreamReader);
			} catch (Exception ex) {
				logger.error("loading properties:{} content failed:{}", propertiesClassLoaderPath, e);
			} finally {
				IOUtils.closeQuietly(inputStreamReader);
			}
		} finally {
			IOUtils.closeQuietly(inputStreamReader);
		}

	}

	public static void setPropertiesToCache(InputStreamReader inputStreamReader) throws IOException {
		Properties properties = new Properties();
		properties.load(inputStreamReader);
		for (String key : properties.stringPropertyNames()) {
			propertiesContentCache.put(key, properties.getProperty(key));
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
