上传文件配置信息
------

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;


@Configuration
@ConditionalOnClass({Servlet.class, StandardServletMultipartResolver.class,
        MultipartConfigElement.class})
@ConditionalOnProperty(prefix = "spring.servlet.multipart", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(MultipartProperties.class)
public class MultipartAutoConfiguration {

    @Autowired
    private MultipartProperties multipartProperties;

    @Bean
    @ConditionalOnMissingBean
    public MultipartConfigElement multipartConfigElement() {
        this.multipartProperties.setMaxFileSize(DataSize.ofGigabytes(2));
        this.multipartProperties.setMaxRequestSize(DataSize.ofGigabytes(4));
        return this.multipartProperties.createMultipartConfig();
    }

    @Bean(name = DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
    @ConditionalOnMissingBean(MultipartResolver.class)
    public StandardServletMultipartResolver multipartResolver() {
        StandardServletMultipartResolver multipartResolver = new StandardServletMultipartResolver();
        multipartResolver.setResolveLazily(true);
        return multipartResolver;
    }

}
```

本地服务器上传到远程服务器
------

```java
HttpHeaders httpHeaders = new HttpHeaders();
headers.setContentType(MediaType.parseMediaType("multipart/form-data"));

MultiValueMap<String, Object> form = new LinkedMultiValueMap<String, Object>();
form.add("file", new FileSystemResource(fileNameWithPath));
	
HttpEntity<MultiValueMap<String, Object>> files = new HttpEntity<>(form, headers);
ResponseEntity<String> responseBean = restTemplate.postForEntity(urlTransfer, files, String.class);
```

从远程服务器下载到本地服务器
------

```java
HttpHeaders headers = new HttpHeaders();
HttpEntity<Resource> httpEntity = new HttpEntity<Resource>(headers);
ResponseEntity<byte[]> response = restTemplate.exchange(getFilePath, HttpMethod.GET, httpEntity, byte[].class);
try {
	File file = new File(path + File.separator + fileName);
	FileOutputStream fos = new FileOutputStream(file);
	fos.write(response.getBody());
	fos.flush();
	fos.close();
} catch (IOException e) {
	e.printStackTrace();
}
```
