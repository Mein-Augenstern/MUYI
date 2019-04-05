package com.demotransfer.module.search.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demotransfer.module.search.engine.ISearcher;
import com.demotransfer.spring.BaseController;
import com.demotransfer.spring.RestResponseBean;

@RestController
@RequestMapping("/api")
public class SearchController extends BaseController {

	@Autowired
	private ISearcher htmlUnitSearcher;

	@RequestMapping(value = "/search", method = RequestMethod.GET)
	public RestResponseBean search(@RequestParam(value = "keyWord") String keyWord,
			@RequestParam(value = "pageNum", required = false) Integer pageNum) {
		if (pageNum == null) {
			return getRightResult(htmlUnitSearcher.search(keyWord));
		}
		return getRightResult(htmlUnitSearcher.search(keyWord, pageNum));
	}

}
