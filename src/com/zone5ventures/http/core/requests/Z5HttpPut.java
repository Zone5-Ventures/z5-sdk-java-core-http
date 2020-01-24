package com.zone5ventures.http.core.requests;

import java.lang.reflect.Type;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;

import com.zone5ventures.core.utils.GsonManager;
import com.zone5ventures.http.core.responses.Z5HttpResponse;
import com.zone5ventures.http.core.responses.Z5HttpResponseJson;

public class Z5HttpPut<T> extends HttpPut implements Z5HttpRequest<T> {
	
	private final Object entity;
	private final Type t;
	
	public Z5HttpPut(Type t, String url, Object entity) {
		super(url);
		this.entity = entity;
		this.t = t;
		
		if (entity != null) {
			StringEntity js = new StringEntity(GsonManager.getInstance().toJson(entity), "UTF-8");
			addHeader("content-type", "application/json");
			setEntity(js);
		}
	}
	
	@Override
	public String toString() {
		return String.format("PUT %s %s", getURI().getPath(), entity == null ? "" : GsonManager.getInstance(true).toJson(entity));
	}
	
	@Override
	public Z5HttpResponse<T> newInstance(CloseableHttpResponse rsp) {
		return new Z5HttpResponseJson<>(t, rsp);
	}
	
	@Override
	public Z5HttpResponse<T> newInstance(Exception e) {
		return new Z5HttpResponseJson<>(e);
	}

}
