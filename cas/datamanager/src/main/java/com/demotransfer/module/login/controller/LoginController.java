package com.demotransfer.module.login.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.demotransfer.module.login.controller.vo.LoginUserInformationVO;
import com.demotransfer.module.login.service.ILoginUserOperationService;
import com.demotransfer.spring.BaseController;
import com.demotransfer.spring.RestResponseBean;

@RestController
@RequestMapping("/api/login")
public class LoginController extends BaseController {

	private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

	@Autowired
	private ILoginUserOperationService loginUserOperationService;

	@RequestMapping(value = "", method = RequestMethod.POST)
	public RestResponseBean login(@RequestBody LoginUserInformationVO loginUserInformationVO) {
		try {
			loginUserOperationService.checkLoginLegain(loginUserInformationVO);
			return getRightResult();
		} catch (Exception e) {
			logger.error("login failed:{}", e);
			return getErrorResult("");
		}
	}

}
