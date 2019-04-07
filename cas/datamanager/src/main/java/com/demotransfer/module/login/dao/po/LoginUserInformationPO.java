package com.demotransfer.module.login.dao.po;

import java.util.Date;

import com.alibaba.fastjson.JSONObject;

/**
 * @Description: The user login information table maps entity classes
 * @author guoyangyang 2019年4月7日 下午12:53:29
 * @version V1.0.0
 */
public class LoginUserInformationPO {

	private String uuid;

	private String userName;

	private String password;

	private Date createTime;

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

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

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

	@Override
	public String toString() {
		return JSONObject.toJSONString(this);
	}

}
