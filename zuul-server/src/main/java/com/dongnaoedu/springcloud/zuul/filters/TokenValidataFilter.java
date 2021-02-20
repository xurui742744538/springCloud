package com.dongnaoedu.springcloud.zuul.filters;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.alibaba.fastjson.JSONObject;
import com.dongnaoedu.springcloud.uaa.jwt.JwtTokenProvider;
import com.dongnaoedu.springcloud.zuul.TonyConfigurationBean;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;

import io.jsonwebtoken.Claims;

/**
 * 验证token。
 */
@Component
public class TokenValidataFilter extends ZuulFilter {
	protected static final Logger logger = LoggerFactory.getLogger(TokenValidataFilter.class);

	// token工具
	JwtTokenProvider jwtTokenProvider;
	// 自定义的配置
	TonyConfigurationBean tonyConfigurationBean;

	public TokenValidataFilter(JwtTokenProvider jwtTokenProvider, TonyConfigurationBean tonyConfigurationBean) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.tonyConfigurationBean = tonyConfigurationBean;
	}

	@Override
	public boolean shouldFilter() {
		RequestContext ctx = RequestContext.getCurrentContext();
		// 根据routeId，过滤掉不需要做权限校验的请求
		return !tonyConfigurationBean.getNoAuthenticationRoutes().contains(ctx.get("proxy"));
	}

	@Override
	public Object run() {
		// zuul中，将当前请求的上下文信息存在线程变量中。取出来
		RequestContext ctx = RequestContext.getCurrentContext();
		// 从上下文中获取httprequest对象
		HttpServletRequest request = ctx.getRequest();
		// 从头部信息中获取Authentication的值，也就是我们的token
		String token = request.getHeader("Authorization");
		if(token == null) {
			forbidden();
			return null;
		}
		// 检验token是否正确
		// 这里只是通过使用key对token进行解码是否成功，并没有对有效期、已经token里面的内容进行校验。
		Claims claims = jwtTokenProvider.parseToken(token);
		if (claims == null) {
			forbidden();	
			return null;
		}
		// 可以将token内容输出出来看看
		logger.debug("当前请求的token内容是：{}", JSONObject.toJSONString(claims));
		// 塞到请求头里面  
		ctx.getZuulRequestHeaders().put("phone",claims.get("phone").toString());
		ctx.getZuulRequestHeaders().put("email",claims.get("email").toString());
		
		return null;
	}

	// 设置response的状态码为403
	void forbidden() {
		// zuul中，将请求附带的信息存在线程变量中。
		RequestContext.getCurrentContext().setResponseStatusCode(HttpStatus.FORBIDDEN.value());
		ReflectionUtils.rethrowRuntimeException(new ZuulException("无访问权限", HttpStatus.FORBIDDEN.value(), "token校验不通过"));
	}

	@Override
	public String filterType() {
		// pre 在发起请求之前会执行这个filter
		return "pre";
	}

	@Override
	public int filterOrder() {
		// 这个是执行顺序，因为同一个类型的filter可能有多个。 值越小越靠前
		return 6;
	}

}
