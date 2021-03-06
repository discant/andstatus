/*
 * Copyright (C) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.net;

import android.text.TextUtils;
import android.util.Base64;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.MyLog;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

public class HttpConnectionBasic extends HttpConnection implements HttpApacheRequest  {
    protected String mPassword;

    @Override
    protected void setConnectionData(HttpConnectionData connectionData) {
        super.setConnectionData(connectionData);
        mPassword = connectionData.dataReader.getDataString(Connection.KEY_PASSWORD, "");
    }  
    
    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        return new HttpApacheUtils(this).postRequest(path);
    }
    
    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        return new HttpApacheUtils(this).postRequest(path, jso);
    }

    @Override
    public JSONObject postRequest(HttpPost postMethod) throws ConnectionException {
        String method = "postRequest";
        String result = "?";
        JSONObject jObj = null;
        int statusCode = 0;
        try {
            HttpClient client = HttpApacheUtils.getHttpClient();
            postMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
            if (getCredentialsPresent()) {
                postMethod.addHeader("Authorization", "Basic " + getCredentials());
            }
            client.getParams().setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, DEFAULT_POST_REQUEST_TIMEOUT);
            client.getParams().setIntParameter(CoreConnectionPNames.SO_TIMEOUT, DEFAULT_POST_REQUEST_TIMEOUT);
            HttpResponse httpResponse = client.execute(postMethod);
            statusCode = httpResponse.getStatusLine().getStatusCode();
            result = retrieveInputStream(httpResponse.getEntity());
            jObj = new JSONObject(result);
            if (jObj != null) {
                String error = jObj.optString("error");
                if ("Could not authenticate you.".equals(error)) {
                    throw new ConnectionException(error);
                }
            }
        } catch (UnsupportedEncodingException e) {
            MyLog.e(this, e);
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, e, result, method);
        } catch (Exception e) {
            MyLog.e(this, "postRequest", e);
            throw new ConnectionException(e);
        } finally {
            postMethod.abort();
        }
        parseStatusCode(statusCode);
        return jObj;
    }

    @Override
    protected final JSONObject getRequest(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return new HttpApacheUtils(this).getRequestAsObject(get);
    }

    @Override
    protected final JSONArray getRequestAsArray(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return new HttpApacheUtils(this).getRequestAsArray(get);
    }
    
    /**
     * Execute a GET request against the Twitter REST API.
     * 
     * @param url
     * @return String
     * @throws ConnectionException
     */
    @Override
    public JSONTokener getRequest(HttpGet getMethod) throws ConnectionException {
        JSONTokener jso = null;
        String response = null;
        boolean ok = false;
        int statusCode = 0;
        HttpClient client = HttpApacheUtils.getHttpClient();
        try {
            getMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
            getMethod.addHeader("Authorization", "Basic " + getCredentials());
            client.getParams().setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, DEFAULT_GET_REQUEST_TIMEOUT);
            client.getParams().setIntParameter(CoreConnectionPNames.SO_TIMEOUT, DEFAULT_GET_REQUEST_TIMEOUT);
            HttpResponse httpResponse = client.execute(getMethod);
            statusCode = httpResponse.getStatusLine().getStatusCode();
            response = retrieveInputStream(httpResponse.getEntity());
            jso = new JSONTokener(response);
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, "getRequest", e);
            throw new ConnectionException(e);
        } finally {
            getMethod.abort();
        }
        parseStatusCode(statusCode);
        if (!ok) {
            jso = null;
        }
        return jso;
    }

    @Override
    public boolean getCredentialsPresent() {
        return !TextUtils.isEmpty(data.accountUsername) 
                && !TextUtils.isEmpty(mPassword);
    }

    @Override
    public void clearAuthInformation() {
        setPassword("");
    }

    @Override
    public boolean isPasswordNeeded() {
        return true;
    }
    /**
     * Set User's password if the Connection object needs it
     */
    @Override
    public void setPassword(String password) {
        if (password == null) {
            password = "";
        }
        if (password.compareTo(mPassword) != 0) {
            mPassword = password;
        }
    }
    @Override
    public String getPassword() {
        return mPassword;
    }
    
    
    /**
     * Retrieve the input stream from the HTTP connection.
     * 
     * @param httpEntity
     * @return String
     */
    private String retrieveInputStream(HttpEntity httpEntity) {
        int length = (int) httpEntity.getContentLength();
        if ( length <= 0 ) {
            // Length is unknown or large
            length = 1024;
        }
        StringBuilder stringBuffer = new StringBuilder(length);
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(httpEntity.getContent(), HTTP.UTF_8);
            char[] buffer = new char[length];
            int count;
            while ((count = inputStreamReader.read(buffer, 0, length - 1)) > 0) {
                stringBuffer.append(buffer, 0, count);
            }
        } catch (UnsupportedEncodingException e) {
            MyLog.e(this, e);
        } catch (IllegalStateException e) {
            MyLog.e(this, e);
        } catch (IOException e) {
            MyLog.e(this, e);
        } finally {
            DbUtils.closeSilently(inputStreamReader);
        }
        return stringBuffer.toString();
    }

    /**
     * Get the HTTP digest authentication. Uses Base64 to encode credentials.
     * 
     * @return String
     */
    private String getCredentials() {
        // TODO: since API9 we will use getBytes(Charset.forName("US-ASCII"))
        return Base64.encodeToString(
                (data.accountUsername + ":" + mPassword).getBytes(),
                Base64.NO_WRAP + Base64.NO_PADDING);
    }

    @Override
    public boolean save(AccountDataWriter dw) {
        boolean changed = super.save(dw);
        
        if (mPassword.compareTo(dw.getDataString(Connection.KEY_PASSWORD, "")) != 0) {
            dw.setDataString(Connection.KEY_PASSWORD, mPassword);
            changed = true;
        }
        
        return changed;
    }
    
    /**
     * Parse the status code and throw appropriate exceptions when necessary.
     * 
     * @param code
     * @throws ConnectionException
     */
    private void parseStatusCode(int code) throws ConnectionException {
        switch (code) {
        case 200:
        case 304:
            break;
        case 401:
            throw new ConnectionException(String.valueOf(code));
        case 400:
        case 403:
        case 404:
            throw new ConnectionException(String.valueOf(code));
        case 500:
        case 502:
        case 503:
            throw new ConnectionException(String.valueOf(code));
        default:
            break;
        }
    }
}
