/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.portlet;

import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

import javax.portlet.ActionURL;
import javax.portlet.MimeResponse;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.RenderURL;
import javax.portlet.ResourceURL;
import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Application;
import org.apache.wicket.IRequestListener;
import org.apache.wicket.SystemMapper;
import org.apache.wicket.core.request.handler.BookmarkableListenerRequestHandler;
import org.apache.wicket.core.request.handler.BookmarkablePageRequestHandler;
import org.apache.wicket.core.request.handler.ListenerRequestHandler;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.core.request.mapper.AbstractComponentMapper;
import org.apache.wicket.portlet.request.mapper.PortletSystemMapper;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.component.IRequestableComponent;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.handler.resource.ResourceReferenceRequestHandler;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.request.resource.PackageResource;
import org.apache.wicket.request.resource.SharedResourceReference;

/**
 * This class is the default request mapper for portlets. It modifies the
 * generated URLs (that are generated by the wrapped SystemMapper) depending on
 * the type of the used request handler. Its purpose is to implement all portlet
 * related functionality from WebRequestCodingStrategy, that was used in Wicket
 * 1.4.
 *
 * @author Peter Pastrnak
 * @author Konstantinos Karavitis
 */
public class PortletRequestMapper extends AbstractComponentMapper {

	public static String PORTLET_URL = "portlet-url";

	private SystemMapper systemMapper;

	public PortletRequestMapper(final Application application) {
		systemMapper = new PortletSystemMapper(application);
	}

	@Override
	public IRequestHandler mapRequest(Request request) {
		return systemMapper.mapRequest(request);
	}

	@Override
	public int getCompatibilityScore(Request request) {
		return systemMapper.getCompatibilityScore(request);
	}

	@Override
	public Url mapHandler(IRequestHandler requestHandler) {
		Url url = systemMapper.mapHandler(requestHandler);
		if (ThreadPortletContext.getPortletRequest() == null || url == null) {
			return url;
		}

		if (requestHandler instanceof RenderPageRequestHandler) {
			if (PortletRequest.RESOURCE_PHASE.equals(ThreadPortletContext.getPortletRequest().getAttribute(PortletRequest.LIFECYCLE_PHASE))) {
				url = encodeRenderUrl(url, true);
			}
		}
		else if (requestHandler instanceof SharedResourceReference) {
			url = encodeSharedResourceUrl(url);
		}
		else if (requestHandler instanceof ResourceReferenceRequestHandler) {
			ResourceReferenceRequestHandler resourceReferenceRequestHandler = (ResourceReferenceRequestHandler) requestHandler;
			IResource resource = resourceReferenceRequestHandler.getResource();

			if (resource instanceof PackageResource) {
				url = encodeSharedResourceUrl(url);
			}
			else {
				url = encodeResourceUrl(url);
			}
		}
		else if (requestHandler instanceof BookmarkablePageRequestHandler) {
			url = encodeRenderUrl(url, true);
		}
		//added mapping for request handlers with type of BookmarkableListenerRequestHandler. The handling is the same as for handlers of type ListenerRequestHandler
		else if (requestHandler instanceof ListenerRequestHandler || requestHandler instanceof BookmarkableListenerRequestHandler) {
			IRequestableComponent component;

			if (requestHandler instanceof ListenerRequestHandler) {
				component = ((ListenerRequestHandler) requestHandler).getComponent();
			}
			else {
				component = ((BookmarkableListenerRequestHandler)requestHandler).getComponent();
			}

			if (component instanceof IRequestListener) {
				url = encodeResourceUrl(url);
			}
			else {
				url = encodeActionUrl(url, ThreadPortletContext.isAjax());
			}
		}

		return url;
	}

	private String getQualifiedPath(String path) {
		HttpServletRequest request = ThreadPortletContext.getHttpServletRequest();
		return request.getServletPath() + "/" + path;
	}

	private Url parseUrl(String urlString) {
		Url url = Url.parse(urlString);

		// to avoid UrlRenderer touching the url (shouldRenderAsFull)
		url.setProtocol(null);
		url.setHost(null);
		url.setPort(null);

		return url;
	}

	private Url encodeResourceUrl(Url url) {
		if (url != null) {
			String qualifiedPath = getQualifiedPath(url.toString());

			PortletResponse portletResponse = ThreadPortletContext.getPortletResponse();
			if ((portletResponse != null) && (portletResponse instanceof MimeResponse)) {
				try {
					ResourceURL resourceUrl = ((MimeResponse) portletResponse).createResourceURL();
					resourceUrl.setResourceID(qualifiedPath);
					qualifiedPath = resourceUrl.toString();

					// resource URLs preserve all request render parameters (at
					// least for Liferay even all POST parameters), so we have
					// to remove all Wicket parameters (that have the portlet
					// namespace), otherwise we would have all submited values
					// in the URL
					int queryStringSeparator = qualifiedPath.indexOf('?');
					if (queryStringSeparator > 0) {
						Map<String, String[]> parameterMap = Utils.parseQueryString(qualifiedPath.substring(queryStringSeparator + 1));

						boolean changed = false;
						Iterator<Map.Entry<String, String[]>> it = parameterMap.entrySet().iterator();
						String namespace = ThreadPortletContext.getNamespace();
						while (it.hasNext()) {
							if (it.next().getKey().startsWith(namespace)) {
								changed = true;
								it.remove();
							}
						}
						if (changed) {
							qualifiedPath = qualifiedPath.substring(0, queryStringSeparator) + '?' + Utils.buildQueryString(parameterMap);
						}
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			url = parseUrl(qualifiedPath);
		}

		return markAsPortletUrl(url);
	}

	private Url encodeSharedResourceUrl(Url url) {
		if (url != null) {
			Request request = RequestCycle.get().getRequest();

			StringBuilder urlBuilder = new StringBuilder();
			urlBuilder.append(request.getContextPath());
			urlBuilder.append(request.getFilterPath());
			urlBuilder.append(PortletFilter.SHARED_RESOURCE_URL_PORTLET_WINDOW_ID_PREFIX);
			urlBuilder.append(Base64.getUrlEncoder().encode(ThreadPortletContext.getWindowID().getBytes()));
			urlBuilder.append('/');
			urlBuilder.append(url.toString());

			url = Url.parse(urlBuilder.toString());
		}

		return url;
	}

	private Url encodeActionUrl(Url url, boolean forceActionUrl) {
		if ((!forceActionUrl) && (ThreadPortletContext.isResourceRequest())) {
			return encodeResourceUrl(url);
		}

		if (url != null) {
			String qualifiedPath = getQualifiedPath(url.toString());

			PortletResponse portletResponse = ThreadPortletContext.getPortletResponse();
			if ((portletResponse != null) && (portletResponse instanceof MimeResponse)) {
				try {
					ActionURL portletUrl = ((MimeResponse) portletResponse).createActionURL();
					portletUrl.getActionParameters().setValue(WicketPortlet.WICKET_URL_PORTLET_PARAMETER, qualifiedPath);
					qualifiedPath = portletUrl.toString();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			url = parseUrl(qualifiedPath);
		}

		return markAsPortletUrl(url);
	}

	private Url encodeRenderUrl(Url url, boolean forceRenderUrl) {
		if ((!forceRenderUrl) && (ThreadPortletContext.isResourceRequest())) {
			return encodeResourceUrl(url);
		}

		if (url != null) {
			String qualifiedPath = getQualifiedPath(url.toString());

			PortletResponse portletResponse = ThreadPortletContext.getPortletResponse();
			if ((portletResponse != null) && (portletResponse instanceof MimeResponse)) {
				try {
					RenderURL portletUrl = ((MimeResponse) portletResponse).createRenderURL();
					portletUrl.getRenderParameters().setValue(WicketPortlet.WICKET_URL_PORTLET_PARAMETER + ThreadPortletContext.getPortletMode().toString(), qualifiedPath);
					qualifiedPath = portletUrl.toString();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			url = parseUrl(qualifiedPath);
		}

		return markAsPortletUrl(url);
	}

	private Url markAsPortletUrl(Url url) {
		url.setQueryParameter(PORTLET_URL, PORTLET_URL);
		return url;
	}
}
