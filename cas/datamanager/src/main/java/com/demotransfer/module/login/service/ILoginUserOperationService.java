package com.demotransfer.module.login.service;

import com.demotransfer.module.login.controller.vo.LoginUserInformationVO;
import com.demotransfer.module.login.service.dto.LoginUserInformationDTO;

/**
 * @Description:User information operation interface
 * @author guoyangyang 2019年4月7日 下午5:52:17
 * @version V1.0.0
 */
public interface ILoginUserOperationService {

	/**
	 * Check that the user login is valid
	 * 
	 * @param loginUserInformationDTO
	 *            {@link com.demotransfer.module.login.service.dto.LoginUserInformationDTO}
	 */
	void checkLoginLegain(LoginUserInformationVO loginUserInformationVO);

}
