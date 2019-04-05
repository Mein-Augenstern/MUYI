package com.demotransfer.module.search.dto;

import java.io.Serializable;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: 网页解析存储模型
 * @author: demotransfer
 * @date: 2018年11月13日 下午10:19:27
 */
public class Webpage implements Serializable {

	private static final long serialVersionUID = 1L;

	// 标题
	private String title;

	// 链接
	private String url;

	// 简介
	private String summary;

	// 正文内容
	private String content;

	public Webpage() {
		super();
	}

	public Webpage(String title, String url, String summary, String content) {
		super();
		this.title = title;
		this.url = url;
		this.summary = summary;
		this.content = content;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	@Override
	public String toString() {
		return JSONObject.toJSONString(this);
	}

}
