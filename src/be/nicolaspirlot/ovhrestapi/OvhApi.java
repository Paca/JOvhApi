package be.nicolaspirlot.ovhrestapi;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import javax.security.sasl.AuthenticationException;

import be.nicolaspirlot.ovhrestapi.exception.InvalidJsonException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class OvhApi {
	public static final String OVH_API_EU = "https://eu.api.ovh.com/1.0";
	public static final String OVH_API_CA = "https://ca.api.ovh.com/1.0";

	private String root;
	private String AK;
	private String AS;
	private String CK;
	private long  timeDrift = 0;

	public OvhApi(final String root, final String AK, final String AS, final String CK)
	{
		this.root = root;
		this.AK = AK;
		this.AS = AS;
		this.CK = CK;

		try
		{
			URL url = new URL(this.root + "/auth/time");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(
					(conn.getInputStream())));

			String output;
			StringBuilder outputData = new StringBuilder();
			while ((output = br.readLine()) != null) {
				outputData.append(output);
			}
			conn.disconnect();
			
			this.timeDrift = (new Date().getTime() / 1000) - Long.parseLong(outputData.toString());
		}
		catch( NumberFormatException e){
			System.err.println("Error in parsing server time to long !");
			e.printStackTrace();
		}
		catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String auth(String accessRules)
	{
		String authUrl = null;
		try
		{
			URL url = new URL(this.root + "/auth/credential");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("X-Ovh-Application", this.AK);
			
			conn.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
			wr.writeBytes(accessRules);
			wr.flush();
			wr.close();
			
			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}			
			
			BufferedReader br = new BufferedReader(new InputStreamReader(
					(conn.getInputStream())));

			String output;
			StringBuilder outputData = new StringBuilder();
			while ((output = br.readLine()) != null) {
				outputData.append(output);
			}
			
			JsonParser parser = new JsonParser();
			JsonElement obj = parser.parse(outputData.toString());
			JsonObject data = obj.getAsJsonObject();
			authUrl = data.get("validationUrl").getAsString();	
			this.CK = data.get("consumerKey").getAsString();
			
			conn.disconnect();
		}
		catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return authUrl;
	}
	
	private String call(String method, String url, String body) 
	{
		String ret = null;			
		try
		{
			if(this.CK == null) throw new AuthenticationException("You must auth first to get your consumerKey(CK)"); 
			url = this.root + url;
			if(body == null)
				body = "";
			else
			{
				//check if body is valid json data.
				try
				{
					JsonElement json = new JsonParser().parse(body);
				}
				catch(JsonParseException e)
				{
					throw new InvalidJsonException("Json body input syntaxException : "+e.getMessage());
				}				
			}
			
			// Compute signature
	        long time = this.getTime();
	        String toSign = this.AS + "+" + this.CK + "+" + method + "+" + url + "+" + body + "+" + time;
	        String signature = "$1$" + sha1(toSign);
	        
			URL urls = new URL(url);
			HttpURLConnection conn = (HttpURLConnection) urls.openConnection();
			conn.setRequestMethod(method);
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Content-Type", "application/json; charset=utf8");
			conn.setRequestProperty("X-Ovh-Application", this.AK);
			conn.setRequestProperty("X-Ovh-Consumer", this.CK);
			conn.setRequestProperty("X-Ovh-Signature", signature);
			conn.setRequestProperty("X-Ovh-Timestamp", "" + time);
			
			if(body != "")
			{
				conn.setDoOutput(true);
				DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
				wr.writeBytes(body);
				wr.flush();
				wr.close();
			}
			
			int responseCode = conn.getResponseCode();
			BufferedReader br = null;
			if(responseCode != 200)
				br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
			else
				br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
			
			String output;
			StringBuilder outputData = new StringBuilder();
			while ((output = br.readLine()) != null) {
				outputData.append(output);
			}
			ret = new String(outputData.toString().getBytes(), "UTF-8");
			
			conn.disconnect();
			
			if (responseCode != 200)
				throw new RuntimeException("Erreur HTTP("+responseCode+") : " + ret);			
		}
		catch( AuthenticationException e){
			System.err.println(e.getMessage());
		}
		catch(InvalidJsonException e)
		{
			System.err.println(e.getMessage());
		}
		catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return ret;
		
	}
	
	public String get(String url)
	{
		return this.call("GET", url, null);
	}

	public String put(String url, String body)
	{
		return this.call("PUT", url, body);
	}
	
	public String post(String url, String body)
	{
		return this.call("POST", url, body);
	}
	
	public String delete(String url)
	{
		return this.call("DELETE", url, null);
	}
	
	// Get ovh time 
	private long getTime()
	{
		// /1000 because ovh server time are less precise than java.
		return (new Date().getTime()/1000) - timeDrift;
	}
	
	// This replicates the PHP sha1 so that we can authenticate the same way.
	public static String sha1(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		return byteArray2Hex(MessageDigest.getInstance("SHA1").digest(s.getBytes("UTF-8")));
	}

	private static final char[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	private static String byteArray2Hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (final byte b : bytes) {
			sb.append(hex[(b & 0xF0) >> 4]);
			sb.append(hex[b & 0x0F]);
		}
		return sb.toString();
	}
}
