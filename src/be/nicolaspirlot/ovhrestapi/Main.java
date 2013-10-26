package be.nicolaspirlot.ovhrestapi;

import java.lang.reflect.Type;
import java.util.List;

import be.nicolaspirlot.ovhrestapi.bill.Bill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class Main {

	public static void main(String[] args) {
		
		String accessRules = "{\"accessRules\":[{\"method\":\"GET\",\"path\":\"/me/bill/*\"}],\"redirection\":\"https://www.exemple.be/\"}";
		OvhApi api = new OvhApi( OvhApi.OVH_API_EU, "YOUR_AK","YOUR_AS", null);
		String authurl = api.auth(accessRules);
		// Use break point here to validate your credential before use api method.
		System.out.println(authurl);
		
		String bills = api.get("/me/bill");
		Gson gson = new GsonBuilder().setDateFormat("YYYY-MM-DD'T'hh:mm:ss").create();
		Type type = new TypeToken<List<String>>() {}.getType();
		List<String> billsList = gson.fromJson(bills, type);
		double priceTot = 0;
		for(String billId : billsList)
		{
			String billDetail = api.get("/me/bill/"+billId);
			
			Type billType = new TypeToken<Bill>() {}.getType();
			Bill bill = gson.fromJson(billDetail, billType);
			priceTot += bill.priceWithTax.value;
			System.out.println(bill.priceWithTax.text + "  ;  " +bill.pdfUrl);
		}
		
		System.out.println("\nPriceWithTax Total : "+ priceTot + " €");
	}

}
