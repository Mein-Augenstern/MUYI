package com.demotransfer.module.search.engine.baidu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demotransfer.module.search.dto.SearchResult;
import com.demotransfer.module.search.dto.Webpage;
import com.demotransfer.module.search.engine.baidu.searcher.HtmlUnitSearcher;

/**
 * @ClassName: SearchKeyHandler
 * @Description: excel文件常见操作
 * @author: Administrator
 * @date: 2018年6月6日 下午10:47:29
 */
public class SearchKeyHandler {

	private static Logger LOG = LoggerFactory.getLogger(SearchKeyHandler.class);
	private static String EXCEL_SUFFIX_XLSX = "xlsx";
	private static String EXCEL_SUFFIX_XLS = "xls";
	private static List<String> FAIL_SEARCH_KEY = Collections.synchronizedList(new ArrayList<String>());// 查询结果为空
	private static List<String> EMPTY_SEARCH_KEY = Collections.synchronizedList(new ArrayList<String>());// 查询结果不满足结果key
	private static String EXIST_FLAG = "1";// 查询结果包含(大于等于)关键字五条以上标志
	private static String NOT_EXIST_FLAG = "0";// 查询结果包含关键字低于(小于)以上标志
	private static Integer TOTAL_EXIST_FLAG = 5;// 查询结果包含总次数
	private static String KEY_WORD_POSSTFIX = "互联网";// 关键字后缀

	private static String result_file_path = "E:\\电气设备.properties";

	public static void main(String[] args) {
		searchMainFunction("E:\\电气设备.xlsx", "xlsx");
	}

	static {
		if (!new File(result_file_path).exists()) {
			try {
				new File(result_file_path).createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @Description: 更新properties文件
	 * @param filePath
	 * @param key
	 * @param value
	 * @return: void
	 */
	public static void updatePropertiesContent(String filePath, String key, String value) {
		Properties prop = new Properties();
		prop.setProperty(key, value);
		FileOutputStream oFile = null;
		try {
			oFile = new FileOutputStream(filePath, true);// true表示追加打开,false每次都是清空再重写
			prop.store(new OutputStreamWriter(oFile, "utf-8"), "lll");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(oFile);
		}
	}

	/**
	 * 加载excel内容,利用keyword查询,比对结果
	 * 
	 * @param excelFilePath
	 *            excel文件内容
	 * @param fileSuffix
	 *            文件后缀名称
	 */
	public static void searchMainFunction(String excelFilePath, String fileSuffix) {
		Workbook workbook = null;
		try {
			if (EXCEL_SUFFIX_XLS.equals(fileSuffix)) {
				workbook = new HSSFWorkbook(new FileInputStream(new File(excelFilePath)));
			} else if (EXCEL_SUFFIX_XLSX.equals(fileSuffix)) {
				workbook = new XSSFWorkbook(new FileInputStream(new File(excelFilePath)));
			}
		} catch (FileNotFoundException e) {
			LOG.info("excel file is not exist:{}", e);
			return;
		} catch (IOException e) {
			LOG.info("read excel has a IOException:{}", e);
			return;
		} finally {

		}

		SearchKeyHandler searchKeyHandler = new SearchKeyHandler();
		for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
			Sheet sheetAt = workbook.getSheetAt(i);
			for (int j = 0; j < sheetAt.getPhysicalNumberOfRows(); j++) {
				Row row = sheetAt.getRow(j);
				String name = "";
				for (int k = 0; k < row.getPhysicalNumberOfCells(); k++) {
					if (k == 0) {
						name = row.getCell(k).getStringCellValue();
						break;
					}
				}

				SearchResult searchResult = searchKeyHandler.searchContentByBaiduSearcher(name + KEY_WORD_POSSTFIX, 2,
						3);
				updateExcel(searchResult, row, name, sheetAt);
			}
		}
	}

	/**
	 * 依据关键字利用百度引擎查询
	 * 
	 * @param keyWord
	 *            查询关键字
	 * @param page
	 *            查询几页(百度查询默认每页10条记录)
	 * @param tryTotalCount
	 *            重试查询次数
	 * @return
	 */
	public SearchResult searchContentByBaiduSearcher(String keyWord, int page, int tryTotalCount) {
		HtmlUnitSearcher htmlUnitSearcher = new HtmlUnitSearcher();
		SearchResult searchResult = null;
		int tryCount = 0;
		while (tryCount < tryTotalCount && searchResult == null) {
			try {
				searchResult = htmlUnitSearcher.search(keyWord, page);
			} catch (Exception e) {
				LOG.info("get searchResult count is:{} has a exception:{}", tryCount, e);
			}
		}
		if (searchResult == null) {
			FAIL_SEARCH_KEY.add(keyWord);
			LOG.info("get searchResult failed. key:{}", keyWord);
		}
		return searchResult;
	}

	/**
	 * 更新excel表格内容
	 * 
	 * @param searchResult
	 *            查询结果
	 * @param row
	 *            excel表格中某一行
	 */
	public static void updateExcel(SearchResult searchResult, Row row, String name, Sheet sheetAt) {
		List<Webpage> webpages = searchResult.getWebpages();
		int count = 0;
		for (Webpage webpage : webpages) {
			String title = webpage.getTitle();
			if (title.contains(KEY_WORD_POSSTFIX)) {
				count++;
			}
		}

		if (count >= TOTAL_EXIST_FLAG) {
			updatePropertiesContent(result_file_path, name, EXIST_FLAG);
		} else {
			EMPTY_SEARCH_KEY.add(name);
			updatePropertiesContent(result_file_path, name, NOT_EXIST_FLAG);
		}
	}
}
