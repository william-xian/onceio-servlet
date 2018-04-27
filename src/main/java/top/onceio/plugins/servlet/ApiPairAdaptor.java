package top.onceio.plugins.servlet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import top.onceio.core.beans.ApiPair;

public class ApiPairAdaptor {
	private ApiPair apiPair;
	public ApiPairAdaptor(ApiPair ap) {
		this.apiPair = ap;
	}

	private static final Gson GSON = new Gson();
	/**
	 * 根据方法参数及其注解，从req（Attr,Param,Body,Cookie)中取出数据
	 * 
	 * @param result
	 * @param req
	 */
	public Object[] resoveReqParams(HttpServletRequest req, HttpServletResponse resp) {
		Map<String,Integer> nameVarIndex = apiPair.getNameVarIndex();
		Map<Class<?>,Integer> typeIndex = apiPair.getTypeIndex();
		Method method = apiPair.getMethod();
		Map<Integer, String> paramNameArgIndex = apiPair.getParamNameArgIndex();
		Map<Integer, String> attrNameArgIndex = apiPair.getAttrNameArgIndex();
		JsonObject json = null;
		try {
			json = GSON.fromJson(req.getReader(), JsonObject.class);
		} catch (JsonSyntaxException | JsonIOException | IOException e) {
			e.printStackTrace();
		}
		if (json == null) {
			json = new JsonObject();
		}
		String uri = req.getRequestURI().substring(req.getContextPath().length());
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String[] uris = uri.split("/");
		for (String name : nameVarIndex.keySet()) {
			Integer i = nameVarIndex.get(name);
			String v = uris[i];
			json.addProperty(name, v);
		}
		Map<String, String[]> map = req.getParameterMap();
		for (Map.Entry<String, String[]> entry : map.entrySet()) {
			String[] vals = entry.getValue();
			String name = entry.getKey();
			String[] ps = name.split("\\.");
			String pname = name;
			JsonObject jobj = json;
			if (ps.length > 0) {
				pname = ps[ps.length - 1];
				jobj = getOrCreateFatherByPath(json, ps);
			}
			if (vals != null && vals.length == 1) {
				jobj.addProperty(pname, vals[0]);
			} else {
				JsonArray ja = new JsonArray();
				for (String v : vals) {
					ja.add(v);
				}
				jobj.add(pname, ja);
			}
		}
		Object[] args = new Object[method.getParameterCount()];
		Class<?>[] types = method.getParameterTypes();
		if (paramNameArgIndex != null && !paramNameArgIndex.isEmpty()) {
			for (Map.Entry<Integer, String> entry : paramNameArgIndex.entrySet()) {
				Class<?> type = types[entry.getKey()];
				if (entry.getValue().equals("")) {
					args[entry.getKey()] = GSON.fromJson(json, type);
				} else {
					args[entry.getKey()] = GSON.fromJson(json.get(entry.getValue()), type);
				}
			}
		}
		if (paramNameArgIndex != null && !paramNameArgIndex.isEmpty()) {
			for (Map.Entry<Integer, String> entry : paramNameArgIndex.entrySet()) {
				Class<?> type = types[entry.getKey()];
				if (entry.getValue().equals("")) {
					args[entry.getKey()] = GSON.fromJson(json, type);
				} else {
					args[entry.getKey()] = GSON.fromJson(json.get(entry.getValue()), type);
				}
			}
		}
		if (attrNameArgIndex != null && !attrNameArgIndex.isEmpty()) {
			for (Map.Entry<Integer, String> entry : attrNameArgIndex.entrySet()) {
				args[entry.getKey()] = req.getAttribute(entry.getValue());
			}
		}
		if (typeIndex != null && !typeIndex.isEmpty()) {
			typeIndex.forEach(new BiConsumer<Class<?>,Integer>() {

				@Override
				public void accept(Class<?> cls, Integer i) {
					if(HttpServletRequest.class.isAssignableFrom(cls)){
						args[i] = req;
					}else if(HttpServletResponse.class.isAssignableFrom(cls)){
						args[i] = resp;
					}
				}
				
			});
		}
		return args;
	}

	private static JsonObject getOrCreateFatherByPath(JsonObject json, String[] ps) {
		JsonObject jobj = json;
		for (int i = 0; i < ps.length - 1; i++) {
			String p = ps[i];
			jobj = jobj.getAsJsonObject(p);
			if (jobj == null) {
				jobj = new JsonObject();
				jobj.add(p, jobj);
			}
		}
		return jobj;
	}

	public Object invoke(HttpServletRequest req, HttpServletResponse resp)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Object[] args = resoveReqParams(req, resp);
		Object obj = apiPair.getMethod().invoke(apiPair.getBean(), args);
		return obj;
	}
}
