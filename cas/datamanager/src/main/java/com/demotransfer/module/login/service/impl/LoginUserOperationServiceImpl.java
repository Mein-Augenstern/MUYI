package com.demotransfer.module.login.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.demotransfer.module.login.controller.vo.LoginUserInformationVO;
import com.demotransfer.module.login.dao.ILoginUserOperationDao;
import com.demotransfer.module.login.dao.po.LoginUserInformationPO;
import com.demotransfer.module.login.service.ILoginUserOperationService;

@Service
public class LoginUserOperationServiceImpl implements ILoginUserOperationService {

	private static final Logger logger = LoggerFactory.getLogger(LoginUserOperationServiceImpl.class);

	@Autowired
	private ILoginUserOperationDao loginUserOperationDao;

	@Override
	public void checkLoginLegain(LoginUserInformationVO loginUserInformationVO) {
		Assert.notNull(loginUserInformationVO, "login user information cannot be null.");

		LoginUserInformationPO loginUserInformationPO = loginUserOperationDao
				.selectLoginUserInformationByUserName(loginUserInformationVO.getUsername());
		if (loginUserInformationPO != null) {
			if (!loginUserInformationPO.getPassword().equals(loginUserInformationVO.getPassword())) {
				logger.error("login user:{} password wrong.", loginUserInformationVO.toString());
			}
		} else {
			logger.error("select user information by loginUserInfomation:{} empty.", loginUserInformationVO.toString());

		}
	}

}
