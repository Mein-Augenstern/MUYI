package com.demotransfer.spring;

import java.io.Serializable;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: rest接口统一返回数据格式
 * @author: demotransfer
 * @date: 2018年11月13日 下午10:20:53
 */
public class RestResponseBean implements Serializable {

	private static final long serialVersionUID = 1L;

	// 错误码
	private String code;

	// 建议
	private String msg;

	// 响应数据
	private Object data;

	public RestResponseBean() {
		super();
	}

	public RestResponseBean(Object data) {
		super();
		this.data = data;
	}

	public RestResponseBean(String code) {
		super();
		this.code = code;
	}

	public RestResponseBean(String code, String msg) {
		super();
		this.code = code;
		this.msg = msg;
	}

	public RestResponseBean(String code, String msg, Object data) {
		super();
		this.code = code;
		this.msg = msg;
		this.data = data;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return JSONObject.toJSONString(this);
	}

}
