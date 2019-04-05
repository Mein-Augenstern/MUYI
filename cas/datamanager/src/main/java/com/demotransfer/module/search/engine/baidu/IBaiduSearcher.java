package com.demotransfer.module.search.engine.baidu;

import com.demotransfer.module.search.dto.SearchResult;
import com.demotransfer.module.search.engine.ISearcher;

/**
 * @Description: 利用百度搜索引擎查询
 * @author: Administrator
 * @date: 2018年6月6日 下午10:48:10
 */
public interface IBaiduSearcher extends ISearcher {

	/**
	 * 新闻搜索
	 * 
	 * @param keyWord
	 * @return
	 */
	SearchResult searchNews(String keyWord);

	/**
	 * 新闻搜索(分页)
	 * 
	 * @param keyword
	 * @param page
	 * @return
	 */
	SearchResult searchNews(String keyword, int page);

	/**
	 * 贴吧搜索
	 * 
	 * @param keyword
	 * @return
	 */
	SearchResult searchTieba(String keyword);

	/**
	 * 贴吧搜索(分页)
	 * 
	 * @param keyword
	 * @param page
	 * @return
	 */
	SearchResult searchTieba(String keyword, int page);

	/**
	 * 知道搜索
	 * 
	 * @param keyword
	 * @return
	 */
	SearchResult searchZhidao(String keyword);

	/**
	 * 知道搜索(分页)
	 * 
	 * @param keyword
	 * @param page
	 * @return
	 */
	SearchResult searchZhidao(String keyword, int page);

	/**
	 * 文库搜索
	 * 
	 * @param keyword
	 * @return
	 */
	SearchResult searchWenku(String keyword);

	/**
	 * 文库搜索(分页)
	 * 
	 * @param keyword
	 * @param page
	 * @return
	 */
	SearchResult searchWenku(String keyword, int page);
}
