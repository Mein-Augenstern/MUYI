package com.demotransfer.module.search.engine.baidu.searcher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demotransfer.module.search.dto.SearchResult;
import com.demotransfer.module.search.engine.baidu.AbstractBaiduSearcher;

public class JSoupBaiduSearcher extends AbstractBaiduSearcher {

	private static final Logger LOG = LoggerFactory.getLogger(JSoupBaiduSearcher.class);

	private static int BAIDU_PAGE_SIZE = 10;// 百度搜索结果每页大小为10

	@Override
	public SearchResult search(String keyWord) {
		return search(keyWord, 1);
	}

	@Override
	public SearchResult search(String keyWord, int page) {
		String targetUrl = "http://www.baidu.com/s?pn=" + (page - 1) * BAIDU_PAGE_SIZE + "&wd=" + keyWord;// pn参数代表的不是页数,而是返回结果的开始数,如获取第一页则pn=0,第二页则pn=10,第三页则pn=20,以此类推,抽象出模式：(page-1)*pageSize
		Document document = null;
		try {
			document = Jsoup.connect(targetUrl).timeout(60000).get();
		} catch (Exception e) {
			LOG.info("Jsoup connect:{} has a exception:{}", targetUrl, e);
		}

		// TODO:s解析网页内容

		SearchResult searchResult = new SearchResult();
		return searchResult;
	}

}
