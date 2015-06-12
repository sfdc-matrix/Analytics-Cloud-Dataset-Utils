/*
 * Copyright (c) 2014, salesforce.com, inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 *    following disclaimer.
 *  
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and 
 *    the following disclaimer in the documentation and/or other materials provided with the distribution. 
 *    
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or 
 *    promote products derived from this software without specific prior written permission.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED 
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.sforce.dataset.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.dataset.DatasetUtilConstants;
import com.sforce.dataset.util.ReleaseType.AssetsType;

public class UpgradeChecker {
	
	final static String latestReleaseURL = "https://api.github.com/repos/forcedotcom/Analytics-Cloud-Dataset-Utils/releases/latest";
	public static final NumberFormat nf = NumberFormat.getIntegerInstance();
	
	public static void main(String[] args) {
		try {
			UpgradeChecker.getLatestJar();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void getLatestJar() throws URISyntaxException, ClientProtocolException, IOException
	{
		CloseableHttpClient httpClient = HttpClients.createDefault();
		
		RequestConfig requestConfig = RequestConfig.custom()
			       .setSocketTimeout(60000)
			       .setConnectTimeout(60000)
			       .setConnectionRequestTimeout(60000)
			       .build();
		   
		URI u = new URI(latestReleaseURL);

		HttpGet listEMPost = new HttpGet(u);

		listEMPost.setConfig(requestConfig);
		CloseableHttpResponse emresponse = httpClient.execute(listEMPost);
		HttpEntity emresponseEntity = emresponse.getEntity();
		InputStream emis = emresponseEntity.getContent();			
		String releases = IOUtils.toString(emis, "UTF-8");
		emis.close();
		httpClient.close();
		
		if(releases!=null && !releases.isEmpty())
		{
			try 
			{
				ObjectMapper mapper = new ObjectMapper();	
				mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
				ReleaseType res =  mapper.readValue(releases, ReleaseType.class);
				List<AssetsType> atList = res.getAssetList();
				if(atList!=null)
				{
					for(AssetsType at:atList)
					{
						if(at.name.endsWith("jar") && at.name.startsWith("datasetutils-"))
						{
							File jar = new File(at.name);
							if(!jar.exists())
							{
								downloadRelease(jar, at.browser_download_url);
							}else
							{
								System.out.println("jar  {"+jar+"} is current. No newer releases found");
							}
						}
					}
				}
//				mapper.writerWithDefaultPrettyPrinter().writeValue(System.out, res);
			} catch (Throwable t) {
				t.printStackTrace();
			}
			}
		}
	
	
	private static void downloadRelease(File jar,String browser_download_url) throws ClientProtocolException, IOException
	{
		CloseableHttpClient httpClient1 = HttpClients.createDefault();
		
		RequestConfig requestConfig = RequestConfig.custom()
			       .setSocketTimeout(60000)
			       .setConnectTimeout(60000)
			       .setConnectionRequestTimeout(60000)
			       .build();

		HttpGet listEMPost1 = new HttpGet(browser_download_url);

		System.out.println("Downloading new release  {"+jar+"}");
		listEMPost1.setConfig(requestConfig);

		long startTime = System.currentTimeMillis();
		long endTime = 0L;
		CloseableHttpResponse emresponse1 = httpClient1.execute(listEMPost1);

	   String reasonPhrase = emresponse1.getStatusLine().getReasonPhrase();
       int statusCode = emresponse1.getStatusLine().getStatusCode();
       if (statusCode != HttpStatus.SC_OK) {
           System.out.println("Method failed: " + reasonPhrase);
	       System.out.println(String.format("%s download failed: %d %s", jar.getAbsolutePath(),statusCode,reasonPhrase));
           return;
       }

		HttpEntity emresponseEntity1 = emresponse1.getEntity();
		InputStream emis1 = emresponseEntity1.getContent();
			FileOutputStream out = new FileOutputStream(jar);
			byte[] buffer = new byte[DatasetUtilConstants.DEFAULT_BUFFER_SIZE];											
			try
			{
				IOUtils.copyLarge(emis1, out, buffer);
				endTime = System.currentTimeMillis();
			}catch(Throwable t)
			{
				endTime = System.currentTimeMillis();
				t.printStackTrace();
			}finally
			{
				out.close();
				emis1.close();
				emresponse1.close();
				httpClient1.close();
			}
			System.out.println("file {"+jar+"} downloaded. Size{"+nf.format(jar.length())+"}, Time{"+nf.format(endTime-startTime)+"}\n");
	}
	
}
