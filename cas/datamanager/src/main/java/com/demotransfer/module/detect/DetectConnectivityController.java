package com.demotransfer.module.detect;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DetectConnectivityController {

	private static Logger LOG = LoggerFactory.getLogger(DetectConnectivityController.class);

	@RequestMapping(value = "/detectConnectivity", method = RequestMethod.GET)
	public String login(HttpServletRequest request, HttpServletResponse response) {
		LOG.debug("controller login:{}", new SimpleDateFormat().format(new Date()));
		return "/hello";
	}

}
