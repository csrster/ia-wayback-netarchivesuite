/* WaybackContext
 *
 * $Id$
 *
 * Created on 5:37:31 PM Apr 20, 2007.
 *
 * Copyright (C) 2007 Internet Archive.
 *
 * This file is part of wayback-webapp.
 *
 * wayback-webapp is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * wayback-webapp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with wayback-webapp; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.wayback.webapp;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.archive.wayback.QueryRenderer;
import org.archive.wayback.ReplayRenderer;
import org.archive.wayback.RequestParser;
import org.archive.wayback.ResourceIndex;
import org.archive.wayback.ResourceStore;
import org.archive.wayback.ResultURIConverter;
import org.archive.wayback.WaybackConstants;
import org.archive.wayback.core.CaptureSearchResults;
import org.archive.wayback.core.Resource;
import org.archive.wayback.core.SearchResult;
import org.archive.wayback.core.SearchResults;
import org.archive.wayback.core.UIResults;
import org.archive.wayback.core.WaybackRequest;
import org.archive.wayback.exception.AccessControlException;
import org.archive.wayback.exception.BadQueryException;
import org.archive.wayback.exception.BetterRequestException;
import org.archive.wayback.exception.ResourceIndexNotAvailableException;
import org.archive.wayback.exception.ResourceNotAvailableException;
import org.archive.wayback.exception.ResourceNotInArchiveException;
import org.archive.wayback.exception.WaybackException;
import org.springframework.beans.factory.BeanNameAware;

/**
 * Retains all information about a particular Wayback configuration
 * withing a ServletContext, including holding references to the
 * implementation instances of the primary Wayback classes:
 * 
 *		ResourceIndex
 *		ResourceStore
 *		QueryUI
 *		ReplayUI  
 *
 * @author brad
 * @version $Date$, $Revision$
 */
public class WaybackContext implements BeanNameAware {

	private int contextPort = 0;
	private String contextName = null;
	private ResourceIndex index = null;
	private ResourceStore store = null;
	private ReplayRenderer replay = null;
	private QueryRenderer query = null;
	private RequestParser parser = null;
	private ResultURIConverter uriConverter = null;

	/**
	 * 
	 */
	public WaybackContext() {
		
	}
	
	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.BeanNameAware#setBeanName(java.lang.String)
	 */
	public void setBeanName(String beanName) {
		// TODO Auto-generated method stub
		this.contextName = "";
		int idx = beanName.indexOf(":");
		if(idx > -1) {
			contextPort = Integer.valueOf(beanName.substring(0,idx));
			contextName = beanName.substring(idx + 1);
		} else {
			try {
				this.contextPort = Integer.valueOf(beanName);
			} catch(NumberFormatException e) {
				e.printStackTrace();
			}
		}
	}
	/**
	 * @param httpRequest
	 * @return the prefix of paths recieved by this server that are handled by
	 * this WaybackContext, including the trailing '/'
	 */
	public String getContextPath(HttpServletRequest httpRequest) {
//		if(contextPort != 0) {
//			return httpRequest.getContextPath();
//		}
		String httpContextPath = httpRequest.getContextPath();
		if(contextName.length() == 0) {
			return httpContextPath + "/";
		}
		return httpContextPath + "/" + contextName + "/";
	}

	/**
	 * @param httpRequest
	 * @param includeQuery
	 * @return the portion of the request following the path to this context
	 * without leading '/'
	 */
	private String translateRequest(HttpServletRequest httpRequest, 
			boolean includeQuery) {

		String origRequestPath = httpRequest.getRequestURI();
		if(includeQuery) {
			String queryString = httpRequest.getQueryString();
			if (queryString != null) {
				origRequestPath += "?" + queryString;
			}
		}
		String contextPath = getContextPath(httpRequest);
		if (!origRequestPath.startsWith(contextPath)) {
			return null;
		}
		return origRequestPath.substring(contextPath.length());
	}
	
	/**
	 * @param httpRequest
	 * @return the portion of the request following the path to this context, 
	 * including any query information,without leading '/'
	 */
	public String translateRequestPathQuery(HttpServletRequest httpRequest) {
		return translateRequest(httpRequest,true);
	}	

	/**
	 * @param httpRequest
	 * @return the portion of the request following the path to this context, 
	 * excluding any query information, without leading '/'
	 */
	public String translateRequestPath(HttpServletRequest httpRequest) {
		return translateRequest(httpRequest,false);
	}	
	
	/**
	 * Construct an absolute URL that points to the root of the context that
	 * recieved the request, including a trailing "/".
	 * 
	 * @return String absolute URL pointing to the Context root where the
	 *         request was revieved.
	 */
	private String getAbsoluteContextPrefix(HttpServletRequest httpRequest, 
			boolean useRequestServer) {
		
		StringBuilder prefix = new StringBuilder();
		prefix.append(WaybackConstants.HTTP_URL_PREFIX);
		String waybackPort = null;
		if(useRequestServer) {
			prefix.append(httpRequest.getLocalName());
			waybackPort = String.valueOf(httpRequest.getLocalPort());
		} else {
			prefix.append(httpRequest.getServerName());
			waybackPort = String.valueOf(httpRequest.getServerPort());
		}
		if (!waybackPort.equals(WaybackConstants.HTTP_DEFAULT_PORT)) {
			prefix.append(":").append(waybackPort);
		}
		String contextPath = getContextPath(httpRequest);
//		if(contextPath.length() > 1) {
//			prefix.append(contextPath);
//		} else {
//			prefix.append(contextPath);
//		}
		prefix.append(contextPath);
		return prefix.toString();
	}
	
	/**
	 * @param httpRequest
	 * @return absolute URL pointing to the base of this WaybackContext, using
	 * Server and port information from the HttpServletRequest argument.
	 */
	public String getAbsoluteServerPrefix(HttpServletRequest httpRequest) {
		return getAbsoluteContextPrefix(httpRequest, true);
	}

	/**
	 * @param httpRequest
	 * @return absolute URL pointing to the base of this WaybackContext, using
	 * Canonical server and port information.
	 */
	public String getAbsoluteLocalPrefix(HttpServletRequest httpRequest) {
		return getAbsoluteContextPrefix(httpRequest, false);
	}

	private boolean dispatchLocal(HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) 
	throws ServletException, IOException {
		
		WaybackRequest wbRequest = new WaybackRequest();
		wbRequest.setContextPrefix(getAbsoluteLocalPrefix(httpRequest));
		UIResults uiResults = new UIResults(wbRequest);
		uiResults.storeInRequest(httpRequest);
		RequestDispatcher dispatcher = null;
		String translated = "/" + translateRequestPathQuery(httpRequest);
//		// special case for the front '/' page:
//		if(translated.length() == 0) {
//			translated = "/";
//		} else {
//			translated = "/" + translated;
//		}
		dispatcher = httpRequest.getRequestDispatcher(translated);
		if(dispatcher != null) {
			dispatcher.forward(httpRequest, httpResponse);
			return true;
		}
		return false;
	}
	
	/**
	 * @param httpRequest
	 * @param httpResponse
	 * @return true if the request was actually handled
	 * @throws ServletException
	 * @throws IOException
	 */
	public boolean handleRequest(HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) 
	throws ServletException, IOException {

		WaybackRequest wbRequest = null;
		boolean handled = false;
		try {

			wbRequest = parser.parse(httpRequest, this);

			if(wbRequest != null) {

				handled = true;
				wbRequest.setContextPrefix(getAbsoluteLocalPrefix(httpRequest));
//				wbRequest.setWbContext(this);
				
				if(wbRequest.isReplayRequest()) {

					// maybe redirect to a better URI for the request given:			
					wbRequest.checkBetterRequest();

					handleReplay(wbRequest,httpRequest,httpResponse);
					
				} else {

					handleQuery(wbRequest,httpRequest,httpResponse);
				}
			} else {
				handled = dispatchLocal(httpRequest,httpResponse);
//				throw new BadQueryException("Unable to understand request");
			}

		} catch (BetterRequestException bre) {

			httpResponse.sendRedirect(bre.getBetterURI());

		} catch (WaybackException e) {
			query.renderException(httpRequest, httpResponse, wbRequest, e);
		}
		return handled;
	}

	private void handleReplay(WaybackRequest wbRequest, 
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) 
	throws IOException, ServletException, ResourceIndexNotAvailableException,
	ResourceNotInArchiveException, BadQueryException, AccessControlException, 
	ResourceNotAvailableException {

			
		SearchResults results = index.query(wbRequest);
		if(!(results instanceof CaptureSearchResults)) {
			throw new ResourceNotAvailableException("Bad results...");
		}
		CaptureSearchResults captureResults = (CaptureSearchResults) results;

		// TODO: check which versions are actually accessible right now?
		SearchResult closest = captureResults.getClosest(wbRequest);
		Resource resource = store.retrieveResource(closest);

		replay.renderResource(httpRequest, httpResponse, wbRequest,
				closest, resource, uriConverter);
	}

	private void handleQuery(WaybackRequest wbRequest, 
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) 
	throws ServletException, IOException, ResourceIndexNotAvailableException, 
	ResourceNotInArchiveException, BadQueryException, AccessControlException {

		SearchResults results = index.query(wbRequest);

		query.renderUrlResults(httpRequest,httpResponse,wbRequest,
				results,uriConverter);
	}

	/**
	 * @param contextPort the contextPort to set
	 */
	public void setContextPort(int contextPort) {
		this.contextPort = contextPort;
	}

	/**
	 * @param contextName the contextName to set
	 */
	public void setContextName(String contextName) {
		this.contextName = contextName;
	}

	/**
	 * @param index the index to set
	 */
	public void setIndex(ResourceIndex index) {
		this.index = index;
	}

	/**
	 * @param store the store to set
	 */
	public void setStore(ResourceStore store) {
		this.store = store;
	}

	/**
	 * @param replay the replay to set
	 */
	public void setReplay(ReplayRenderer replay) {
		this.replay = replay;
	}

	/**
	 * @param query the query to set
	 */
	public void setQuery(QueryRenderer query) {
		this.query = query;
	}

	/**
	 * @param parser the parser to set
	 */
	public void setParser(RequestParser parser) {
		this.parser = parser;
	}

	/**
	 * @param uriConverter the uriConverter to set
	 */
	public void setUriConverter(ResultURIConverter uriConverter) {
		this.uriConverter = uriConverter;
	}


	/**
	 * @return the contextPort
	 */
	public int getContextPort() {
		return contextPort;
	}
}
