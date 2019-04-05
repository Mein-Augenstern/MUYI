package com.demotransfer.spring;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.demotransfer.exception.ProgramException;

public abstract class BaseController {

	private static final Logger logger = LoggerFactory.getLogger(BaseController.class);

	@ExceptionHandler
	public RestResponseBean executeControllerException(HttpServletRequest request, HttpServletResponse response, RuntimeException ex) {
		logger.warn("Unknown RuntimeException:{}", ex);
		return getErrorResult(ex);
	}

	public RestResponseBean getRightResult() {
		return new RestResponseBean("0");
	}

	public RestResponseBean getRightResult(Object data) {
		return new RestResponseBean(data);
	}

	public RestResponseBean getErrorResult(String errorCode) {
		return new RestResponseBean(errorCode);
	}

	public RestResponseBean getErrorResult(RuntimeException ex) {
		if (ex instanceof ProgramException) {
			return new RestResponseBean(((ProgramException) ex).getCode());
		}
		return getErrorResult("unknown error");
	}

}
