package top.onceio.plugins.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import top.onceio.core.annotation.BeansIn;
import top.onceio.core.beans.ApiMethod;
import top.onceio.core.beans.ApiPair;
import top.onceio.core.beans.ApiResover;
import top.onceio.core.beans.BeansEden;
import top.onceio.core.exception.Failed;

public class OIODispatcherServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final static Gson GSON = new Gson();

	@Override
	public void init() throws ServletException {
		super.init();
		String launcherClass = getInitParameter("launcher");
		Class<?> cnf;
		try {
			cnf = Class.forName(launcherClass);
			loadBeans(cnf);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static void loadBeans(Class<?> cnf) {
		BeansIn beansPackage = cnf.getDeclaredAnnotation(BeansIn.class);
		if (beansPackage != null && beansPackage.value().length != 0) {
			BeansEden.get().resovle(beansPackage.value());
		} else {
			String pkg = cnf.getPackage().getName();
			BeansEden.get().resovle(pkg);
		}

	}

	@Override
	public void destroy() {
		super.destroy();
	}

	@Override
	public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		HttpServletRequest req;
		HttpServletResponse resp;
		if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
			throw new ServletException("non-HTTP request or response");
		}
		req = (HttpServletRequest) request;
		resp = (HttpServletResponse) response;
		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");
		resp.setHeader("Content-type", "application/json;charset=UTF-8");
		String localUri = req.getRequestURI().substring(req.getContextPath().length());
		ApiPair apiPair = search(ApiMethod.valueOf(req.getMethod()),localUri);
		ApiPairAdaptor adaptor = new ApiPairAdaptor(apiPair);
		if (apiPair != null) {
			try {
				Object obj = adaptor.invoke(req, resp);
				if (obj != null) {
					PrintWriter writer = resp.getWriter();
					GSON.toJson(obj, writer);
					writer.close();
				}
			} catch (InvocationTargetException e) {
				Throwable target = e.getTargetException();
				if (target instanceof Failed) {
					Failed failed = (Failed) target;
					Map<String, Object> r = new HashMap<>();
					r.put("msg", String.format(failed.getFormat(), failed.getArgs()));
					r.put("data", failed.getData());
					PrintWriter writer = resp.getWriter();
					GSON.toJson(r, writer);
					writer.close();
				}
			} catch (IllegalAccessException | IllegalArgumentException e) {
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				e.printStackTrace();
			}
		} else {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND,
					String.format("Not found: %s %s", req.getMethod(), req.getRequestURI()));
		}
	}
	
	
	/**
	 * TODO O3
	 * 	  api 
	 * 1. /a/ 
	 * 2. /a 
	 * 3. /a/b 
	 * 4. /a/{v1} 
	 * 5. /a/{v1}/b 
	 * 6. /a/{v1}/{v2}
	 */
	public ApiPair search(ApiMethod apiMethod, String uri) {
		ApiResover ar = BeansEden.get().getApiResover();
		String target = apiMethod.name() + ":" + uri;
		for(String api:ar.getApis()) {
			if (target.matches(api)) {
				return ar.getPatternToApi().get(api);
			}
		}
		return null;
	}
	
	
}