package com.demotransfer.module.login.service.dto;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: Log into the user information model
 * @author guoyangyang 2019年4月7日 下午9:11:18
 * @version V1.0.0
 */
public class LoginUserInformationDTO {

	private String userName;

	private String password;

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
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
