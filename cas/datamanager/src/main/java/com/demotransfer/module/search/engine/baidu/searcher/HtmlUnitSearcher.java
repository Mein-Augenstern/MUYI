package com.demotransfer.module.search.engine.baidu.searcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.demotransfer.module.search.dto.SearchResult;
import com.demotransfer.module.search.dto.Webpage;
import com.demotransfer.module.search.engine.baidu.AbstractBaiduSearcher;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @Description: 关于htmlunit的相关资料，在此站上有些资料，参考了一下：http://www.cnblogs.com/cation/p/
 *               3933408.html
 * @author: Administrator
 * @date: 2018年6月6日 下午10:47:17
 */
@Service(value = "htmlUnitSearcher")
public class HtmlUnitSearcher extends AbstractBaiduSearcher {

	private static final Logger LOG = LoggerFactory.getLogger(HtmlUnitSearcher.class);
	private static HtmlPage firstBaiduPage;// 保存第一页搜索结果
	private static String format = "";// Baidu对应每个搜索结果的第一页第二页第三页等等其中包含“&pn=1”,“&pn=2”,“&pn=3”等等,
										// 提取该链接并处理可以获取到一个模板，用于定位某页搜索结果

	@Override
	public SearchResult search(String keyWord) {
		return search(keyWord, 1);
	}

	@Override
	public SearchResult search(String keyWord, int page) {
		LOG.warn("keyWord:{} page:{}", keyWord, page);

		SearchResult searchResult = new SearchResult();
		searchResult.setPage(page);
		List<Webpage> webpages = new ArrayList<Webpage>();
		searchResult.setWebpages(webpages);

		// 1.获取并输出第一页百度查询内容
		Elements firstPageURL = null;
		try {
			firstPageURL = getFirstPage(keyWord);
		} catch (FailingHttpStatusCodeException | IOException e) {
			LOG.info("get first page has a exception:{}", e);
			return null;
		}
		for (Element newlink : firstPageURL) {// 定义firstPageURL作为第一个搜索页的元素集
			String linkHref = newlink.attr("href");// 提取包含“href”的元素成分，JSoup实现内部具体过程
			String linkText = newlink.text();// 声明变量用于保存每个链接的摘要
			if (linkHref.length() > 14 & linkText.length() > 2) {// 去除某些无效链接(目前是通过标题和连接长度来过滤)
				Webpage webpage = new Webpage();
				webpage.setTitle(linkText);// 标题
				webpage.setUrl(linkHref);// 标题链接
				webpages.add(webpage);
			}
		}

		// 2.读取第二页及之后页面预处理
		nextHref(firstBaiduPage);// 以firstBaiduPage作为参数，定义format，即网页格式。

		// 3.获取百度第一页之后的搜索结果
		for (int i = 1; i < page; i++) {
			System.err.println("\n************百度搜索“" + keyWord + "”第" + (i + 1) + "页结果************");
			String tempURL = format.replaceAll("&pn=1", "&pn=" + i + "");// 根据已知格式修改生成新的一页的链接
			System.err.println("该页地址为：" + format.replaceAll("&pn=1", "&pn=" + i + ""));// 显示该搜索模板

			HtmlUnitSearcher htmlUnitSearcher = new HtmlUnitSearcher();
			String htmls = htmlUnitSearcher.getPageSource(tempURL, "utf-8");// 不知为何此处直接用JSoup的相关代码摘取网页内容会出现问题，所以采用新的编码来实现摘取网页源码
			org.jsoup.nodes.Document doc = Jsoup.parse(htmls);// 网页信息转换为jsoup可识别的doc模式
			Elements links = doc.select("a[data-click]");// 摘取该页搜索链接
			for (Element newlink : links) {// 该处同上getFirstPage的相关实现
				String linkHref = newlink.attr("href");
				String linkText = newlink.text();
				if (linkHref.length() > 14 & linkText.length() > 2) {// 去除某些无效链接(目前是通过标题和连接长度来过滤)
					Webpage webpage = new Webpage();
					webpage.setTitle(linkText);// 标题
					webpage.setUrl(linkHref);// 标题链接
					webpages.add(webpage);
				}
			}
		}
		return searchResult;
	}

	/**
	 * 获取百度搜索第一页内容
	 * 
	 * @param w
	 * @return
	 * @throws FailingHttpStatusCodeException
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	@SuppressWarnings("resource")
	public static Elements getFirstPage(String w) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		WebClient webClient = new WebClient(BrowserVersion.CHROME);// 创建WebClient
		webClient.getOptions().setJavaScriptEnabled(false);// HtmlUnit对JavaScript的支持不好，关闭之
		webClient.getOptions().setCssEnabled(false);// HtmlUnit对CSS的支持不好，关闭之
		HtmlPage page = (HtmlPage) webClient.getPage("http://www.baidu.com/");// 百度搜索首页页面
		HtmlInput input = (HtmlInput) page.getHtmlElementById("kw");// 获取搜索输入框并提交搜索内容（查看源码获取元素名称）
		input.setValueAttribute(w);// 将搜索词模拟填进百度输入框（元素ID如上）
		HtmlInput btn = (HtmlInput) page.getHtmlElementById("su");// 获取搜索按钮并点击
		firstBaiduPage = btn.click();// 模拟搜索按钮事件
		String WebString = firstBaiduPage.asXml().toString();// 将获取到的百度搜索的第一页信息输出
		org.jsoup.nodes.Document doc = Jsoup.parse(WebString);// 转换为Jsoup识别的doc格式

		Elements links = doc.select("a[data-click]");// 返回包含类似<a......data-click=" "......>等的元素，详查JsoupAPI
		return links;// 返回此类链接，即第一页的百度搜素链接
	}

	/**
	 * 获取下一页地址
	 * 
	 * @param p
	 */
	@SuppressWarnings("resource")
	public static void nextHref(HtmlPage p) {
		// 输入：HtmlPage格式变量，第一页的网页内容；
		// 输出：format的模板
		WebClient webClient = new WebClient(BrowserVersion.CHROME);
		webClient.getOptions().setJavaScriptEnabled(false);
		webClient.getOptions().setCssEnabled(false);

		p = firstBaiduPage;
		String morelinks = p.getElementById("page").asXml();// 获取到百度第一页搜索的底端的页码的html代码
		org.jsoup.nodes.Document doc = Jsoup.parse(morelinks);// 转换为Jsoup识别的doc格式
		Elements links = doc.select("a[href]");// 提取这个html中的包含<a href=""....>的部分
		boolean getFormat = true;// 设置只取一次每页链接的模板格式
		for (Element newlink : links) {
			String linkHref = newlink.attr("href");// 将提取出来的<a>标签中的链接取出
			if (getFormat) {
				format = "http://www.baidu.com" + linkHref;// 补全模板格式
				getFormat = false;
			}
		}
	}

	public String getPageSource(String pageUrl, String encoding) {
		// 输入：url链接&编码格式
		// 输出：该网页内容
		StringBuffer sb = new StringBuffer();
		try {
			URL url = new URL(pageUrl);// 构建一URL对象
			BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), encoding));// 使用openStream得到一输入流并由此构造一个BufferedReader对象
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line);
				sb.append("\n");
			}
			in.close();
		} catch (Exception ex) {
			System.err.println(ex);
		}
		return sb.toString();
	}

}
