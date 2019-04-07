package com.demotransfer.module.login.controller.vo;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: User login information front segment model
 * @author guoyangyang 2019年4月7日 下午9:35:09
 * @version V1.0.0
 */
public class LoginUserInformationVO {

	private String username;

	private String password;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public String toString() {
		return JSONObject.toJSONString(this);
	}

}
