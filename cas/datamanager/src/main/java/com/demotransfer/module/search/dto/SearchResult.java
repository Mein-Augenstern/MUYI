package com.demotransfer.module.search.dto;

import java.io.Serializable;
import java.util.List;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: 搜索结果模型
 * @author: Administrator
 * @date: 2018年11月13日 下午10:13:16
 */
public class SearchResult implements Serializable {

	private static final long serialVersionUID = 1L;

	// 总的搜索结果数
	private int total;
	// 第几页
	private int page;
	// 页面数据
	private List<Webpage> webpages;

	public SearchResult() {
		super();
	}

	public SearchResult(int total, int page, List<Webpage> webpages) {
		super();
		this.total = total;
		this.page = page;
		this.webpages = webpages;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public List<Webpage> getWebpages() {
		return webpages;
	}

	public void setWebpages(List<Webpage> webpages) {
		this.webpages = webpages;
	}

	public int getPageSize() {
		return webpages.size();
	}

	@Override
	public String toString() {
		return JSONObject.toJSONString(this);
	}

}
