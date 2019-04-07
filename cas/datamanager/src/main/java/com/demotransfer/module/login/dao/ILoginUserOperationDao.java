package com.demotransfer.module.login.dao;

import org.apache.ibatis.annotations.Mapper;

import com.demotransfer.module.login.dao.po.LoginUserInformationPO;

/**
 * @Description: User information operation interface
 * @author guoyangyang 2019年4月7日 下午12:52:20
 * @version V1.0.0
 */
@Mapper
public interface ILoginUserOperationDao {

	/**
	 * Query user login information by user unique name
	 * 
	 * @param username
	 * @return {@link com.demotransfer.module.login.dao.po.LoginUserInformationPO}
	 */
	LoginUserInformationPO selectLoginUserInformationByUserName(String username);

}
