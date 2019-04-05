package com.demotransfer.module.search.engine;

import com.demotransfer.module.search.dto.SearchResult;

/**
 * @ClassName: ISearcher
 * @Description: 搜索接口功能定义
 * @author: Administrator
 * @date: 2018年6月6日 下午10:48:34
 */
public interface ISearcher {

	/**
	 * 依据关键字查询信息
	 * 
	 * @param keyWord
	 *            关键字
	 * @return
	 */
	SearchResult search(String keyWord);

	/**
	 * 依据关键字查询信息(分页)
	 * 
	 * @param keyWord
	 *            关键字
	 * @param page
	 * @return
	 */
	SearchResult search(String keyWord, int page);
}
